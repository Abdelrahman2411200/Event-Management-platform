package com.eventplatform.attendee.application;

import com.eventplatform.attendee.api.RequestContext;
import com.eventplatform.attendee.domain.AttendeeProfile;
import com.eventplatform.attendee.domain.AttendeeProfileRepository;
import com.eventplatform.attendee.domain.Booking;
import com.eventplatform.attendee.domain.BookingLineItem;
import com.eventplatform.attendee.domain.BookingLineItemRepository;
import com.eventplatform.attendee.domain.BookingRepository;
import com.eventplatform.attendee.domain.BookingSaga;
import com.eventplatform.attendee.domain.BookingSagaRepository;
import com.eventplatform.attendee.domain.BookingSagaState;
import com.eventplatform.attendee.domain.RegistrationRepository;
import com.eventplatform.attendee.domain.Ticket;
import com.eventplatform.attendee.domain.TicketHold;
import com.eventplatform.attendee.domain.TicketHoldRepository;
import com.eventplatform.attendee.domain.TicketHoldStatus;
import com.eventplatform.attendee.domain.TicketRepository;
import com.eventplatform.attendee.domain.TicketStatus;
import com.eventplatform.attendee.integration.AttendeeLifecycleEvents;
import com.eventplatform.attendee.outbox.AttendeeTransactionalOutbox;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingSagaService {
    private final BookingSagaRepository sagas;
    private final BookingRepository bookings;
    private final RegistrationRepository registrations;
    private final BookingLineItemRepository lines;
    private final TicketHoldRepository holds;
    private final TicketRepository tickets;
    private final AttendeeProfileRepository profiles;
    private final AttendeeTransactionalOutbox outbox;
    private final QrTokenService qrTokenService;
    private final Clock clock;

    public BookingSagaService(
            BookingSagaRepository sagas,
            BookingRepository bookings,
            RegistrationRepository registrations,
            BookingLineItemRepository lines,
            TicketHoldRepository holds,
            TicketRepository tickets,
            AttendeeProfileRepository profiles,
            AttendeeTransactionalOutbox outbox,
            QrTokenService qrTokenService,
            Clock clock) {
        this.sagas = sagas;
        this.bookings = bookings;
        this.registrations = registrations;
        this.lines = lines;
        this.holds = holds;
        this.tickets = tickets;
        this.profiles = profiles;
        this.outbox = outbox;
        this.qrTokenService = qrTokenService;
        this.clock = clock;
    }

    @Transactional
    public void paymentProcessing(
            UUID bookingId, UUID paymentId, UUID attendeeId, BigDecimal amount, String currency) {
        Booking booking = matching(bookingId, attendeeId, amount, currency);
        BookingSaga saga = sagas.findByIdForUpdate(bookingId).orElseThrow();
        if (saga.getState() == BookingSagaState.PAYMENT_PENDING) {
            Instant now = clock.instant();
            saga.paymentProcessing(paymentId, now);
            booking.paymentProcessing(now);
        }
    }

    @Transactional
    public void paymentSucceeded(
            UUID bookingId, UUID paymentId, UUID attendeeId, BigDecimal amount, String currency,
            RequestContext context) {
        Booking booking = matching(bookingId, attendeeId, amount, currency);
        BookingSaga saga = sagas.findByIdForUpdate(bookingId).orElseThrow();
        if (Set.of(BookingSagaState.CONFIRMED, BookingSagaState.REFUNDED, BookingSagaState.PARTIALLY_REFUNDED)
                .contains(saga.getState())) return;
        TicketHold hold = holds.findByBookingId(bookingId).orElseThrow();
        Instant now = clock.instant();
        if (hold.getStatus() == TicketHoldStatus.CONFIRMED) {
            finalizeConfirmation(booking, saga, hold, context, now);
            return;
        }
        if (hold.getStatus() != TicketHoldStatus.ACTIVE || !hold.getExpiresAt().isAfter(now)) {
            compensate(booking, saga, paymentId, "INVENTORY_HOLD_UNAVAILABLE",
                    "Payment settled after the inventory hold became unavailable", context, now);
            return;
        }
        saga.confirmationPending(paymentId, now);
        booking.confirmationPending(now);
        appendInventoryCommand(AttendeeLifecycleEvents.INVENTORY_CONFIRMATION_REQUESTED,
                booking, saga, hold, context, now);
    }

    @Transactional
    public void paymentFailed(
            UUID bookingId, UUID paymentId, UUID attendeeId, String code, String reason,
            RequestContext context) {
        Booking booking = bookings.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || !booking.getAttendeeId().equals(attendeeId)) return;
        BookingSaga saga = sagas.findByIdForUpdate(bookingId).orElseThrow();
        if (Set.of(
                BookingSagaState.CONFIRMED, BookingSagaState.REFUNDED, BookingSagaState.PARTIALLY_REFUNDED,
                BookingSagaState.INVENTORY_CONFIRMATION_PENDING, BookingSagaState.COMPENSATION_PENDING)
                .contains(saga.getState())) return;
        Instant now = clock.instant();
        saga.paymentFailed(paymentId, code, reason, now);
        booking.paymentFailed(now);
        TicketHold hold = holds.findByBookingId(bookingId).orElseThrow();
        if (hold.getStatus() == TicketHoldStatus.ACTIVE) {
            hold.release(now);
            appendInventoryCommand(AttendeeLifecycleEvents.INVENTORY_RELEASE_REQUESTED,
                    booking, saga, hold, context, now);
        }
        outbox.append("Booking", bookingId, AttendeeLifecycleEvents.BOOKING_PAYMENT_FAILED, 1,
                new AttendeeLifecycleEvents.BookingSagaChangedV1(
                        bookingId, paymentId, attendeeId, booking.getEventId(), booking.getStatus(), code, now),
                context, now);
    }

    @Transactional
    public void inventoryConfirmed(UUID reservationId, RequestContext context) {
        TicketHold hold = holds.findByInventoryReservationIdForUpdate(reservationId).orElse(null);
        if (hold == null) return;
        Booking booking = bookings.findByIdForUpdate(hold.getBookingId()).orElseThrow();
        BookingSaga saga = sagas.findByIdForUpdate(booking.getId()).orElseThrow();
        Instant now = clock.instant();
        hold.confirm(now);
        if (saga.getState() == BookingSagaState.INVENTORY_CONFIRMATION_PENDING) {
            finalizeConfirmation(booking, saga, hold, context, now);
        }
    }

    @Transactional
    public void inventoryUnavailable(UUID reservationId, String code, String reason, RequestContext context) {
        TicketHold hold = holds.findByInventoryReservationIdForUpdate(reservationId).orElse(null);
        if (hold != null) handleUnavailable(hold, code, reason, context);
    }

    @Transactional
    public void holdExpired(UUID holdId, RequestContext context) {
        TicketHold hold = holds.findByIdForUpdate(holdId).orElse(null);
        if (hold != null) handleUnavailable(
                hold, "INVENTORY_HOLD_EXPIRED", "The local inventory hold deadline elapsed", context);
    }

    @Transactional
    public void refunded(
            UUID bookingId, UUID paymentId, UUID attendeeId, BigDecimal amount, String currency,
            List<UUID> ticketIds, boolean full, RequestContext context) {
        Booking booking = matchingRefund(bookingId, attendeeId, currency);
        BookingSaga saga = sagas.findByIdForUpdate(bookingId).orElseThrow();
        if (saga.getState() == BookingSagaState.REFUNDED) return;
        Instant now = clock.instant();
        List<Ticket> owned = tickets.findAllByBookingIdForUpdate(bookingId);
        Set<UUID> selected = ticketIds == null || ticketIds.isEmpty()
                ? owned.stream().map(Ticket::getId).collect(Collectors.toSet()) : Set.copyOf(ticketIds);
        owned.stream()
                .filter(ticket -> selected.contains(ticket.getId()) && ticket.getStatus() != TicketStatus.REFUNDED)
                .forEach(ticket -> ticket.refund(now));
        if (full) {
            booking.refund(now);
            registrations.findById(booking.getRegistrationId()).ifPresent(registration -> registration.refund(now));
        } else {
            booking.partiallyRefunded(now);
        }
        saga.refunded(full, now);
        outbox.append("Booking", bookingId, AttendeeLifecycleEvents.BOOKING_REFUNDED, 1,
                new AttendeeLifecycleEvents.BookingRefundedV1(
                        bookingId, paymentId, attendeeId, booking.getEventId(), amount, currency,
                        List.copyOf(selected), full, now), context, now);
    }

    @Transactional
    public void recover(UUID bookingId) {
        BookingSaga saga = sagas.findByIdForUpdate(bookingId).orElse(null);
        if (saga == null) return;
        Booking booking = bookings.findByIdForUpdate(bookingId).orElseThrow();
        TicketHold hold = holds.findByBookingId(bookingId).orElseThrow();
        Instant now = clock.instant();
        RequestContext context = RequestContext.system("booking-saga-recovery", bookingId.toString());
        if (saga.getState() == BookingSagaState.INVENTORY_CONFIRMATION_PENDING) {
            appendInventoryCommand(AttendeeLifecycleEvents.INVENTORY_CONFIRMATION_REQUESTED,
                    booking, saga, hold, context, now);
        } else if (saga.getState() == BookingSagaState.COMPENSATION_PENDING) {
            appendCompensation(booking, saga, context, now);
        }
        saga.retry(now);
    }

    private void finalizeConfirmation(
            Booking booking, BookingSaga saga, TicketHold hold, RequestContext context, Instant now) {
        hold.confirm(now);
        booking.confirm(now);
        registrations.findById(booking.getRegistrationId()).ifPresent(registration -> registration.confirm(now));
        saga.confirmed(now);
        if (tickets.findAllByBookingIdOrderByIssuedAt(booking.getId()).isEmpty()) {
            BookingLineItem line = lines.findAllByBookingId(booking.getId()).stream().findFirst().orElseThrow();
            AttendeeProfile profile = profiles.findById(booking.getAttendeeId()).orElseThrow();
            for (int sequence = 0; sequence < line.getQuantity(); sequence++) {
                Ticket ticket = tickets.save(new Ticket(
                        UUID.randomUUID(), booking.getId(), booking.getRegistrationId(), line,
                        booking.getAttendeeId(), now));
                outbox.append("Ticket", ticket.getId(), AttendeeLifecycleEvents.TICKET_ISSUED, 1,
                        new AttendeeLifecycleEvents.TicketIssuedV1(
                                ticket.getId(), booking.getId(), booking.getAttendeeId(), booking.getEventId(),
                                ticket.getTicketTypeId(), ticket.getEventTitle(), ticket.getTicketTypeName(),
                                ticket.getEventStartsAt(), profile.getEmail(), profile.getPhoneNumber(),
                                profile.getLocale(), qrTokenService.issue(ticket), now), context, now);
            }
        }
        outbox.append("Booking", booking.getId(), AttendeeLifecycleEvents.BOOKING_CONFIRMED, 1,
                new AttendeeLifecycleEvents.BookingSagaChangedV1(
                        booking.getId(), saga.getPaymentId(), booking.getAttendeeId(), booking.getEventId(),
                        booking.getStatus(), null, now), context, now);
    }

    private void compensate(
            Booking booking, BookingSaga saga, UUID paymentId, String code, String reason,
            RequestContext context, Instant now) {
        saga.compensate(paymentId, code, reason, now);
        booking.compensationPending(now);
        appendCompensation(booking, saga, context, now);
    }

    private void handleUnavailable(TicketHold hold, String code, String reason, RequestContext context) {
        if (hold.getStatus() != TicketHoldStatus.ACTIVE) return;
        Booking booking = bookings.findByIdForUpdate(hold.getBookingId()).orElseThrow();
        BookingSaga saga = sagas.findByIdForUpdate(booking.getId()).orElseThrow();
        Instant now = clock.instant();
        hold.expire(now);
        if (saga.getPaymentId() != null && Set.of(
                BookingSagaState.INVENTORY_CONFIRMATION_PENDING, BookingSagaState.PAYMENT_PROCESSING)
                .contains(saga.getState())) {
            compensate(booking, saga, saga.getPaymentId(), code, reason, context, now);
        } else {
            saga.expired(now);
            booking.expire(now);
            registrations.findById(booking.getRegistrationId()).ifPresent(registration -> registration.expire(now));
            outbox.append("TicketHold", hold.getId(), AttendeeLifecycleEvents.TICKET_HOLD_EXPIRED, 1,
                    new AttendeeLifecycleEvents.TicketHoldExpiredV1(
                            hold.getId(), booking.getId(), booking.getAttendeeId(), booking.getEventId(),
                            hold.getTicketTypeId(), hold.getInventoryReservationId(), hold.getQuantity(), now),
                    context, now);
        }
    }

    private void appendCompensation(
            Booking booking, BookingSaga saga, RequestContext context, Instant now) {
        outbox.append("Booking", booking.getId(), AttendeeLifecycleEvents.PAYMENT_COMPENSATION_REQUESTED, 1,
                new AttendeeLifecycleEvents.PaymentCompensationRequestedV1(
                        booking.getId(), saga.getPaymentId(), booking.getAttendeeId(), saga.getFailureReason(), now),
                context, now);
    }

    private void appendInventoryCommand(
            String type, Booking booking, BookingSaga saga, TicketHold hold,
            RequestContext context, Instant now) {
        outbox.append("Booking", booking.getId(), type, 1,
                new AttendeeLifecycleEvents.InventorySagaCommandV1(
                        booking.getId(), saga.getPaymentId(), booking.getAttendeeId(), booking.getEventId(),
                        hold.getTicketTypeId(), hold.getInventoryReservationId(), hold.getQuantity(),
                        type + ":" + booking.getId(), now), context, now);
    }

    private Booking matching(UUID id, UUID attendeeId, BigDecimal amount, String currency) {
        Booking booking = bookings.findByIdForUpdate(id).orElseThrow();
        if (!booking.getAttendeeId().equals(attendeeId)
                || booking.getTotalAmount().compareTo(amount) != 0
                || !booking.getCurrency().equals(currency)) {
            throw new IllegalArgumentException("Payment event does not match the booking snapshot");
        }
        return booking;
    }

    private Booking matchingRefund(UUID id, UUID attendeeId, String currency) {
        Booking booking = bookings.findByIdForUpdate(id).orElseThrow();
        if (!booking.getAttendeeId().equals(attendeeId) || !booking.getCurrency().equals(currency)) {
            throw new IllegalArgumentException("Refund event does not match the booking snapshot");
        }
        return booking;
    }
}
