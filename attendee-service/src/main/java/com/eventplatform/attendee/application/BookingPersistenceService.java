package com.eventplatform.attendee.application;

import com.eventplatform.attendee.api.AttendeeApi;
import com.eventplatform.attendee.api.AttendeeApiException;
import com.eventplatform.attendee.api.RequestContext;
import com.eventplatform.attendee.domain.Booking;
import com.eventplatform.attendee.domain.BookingCommand;
import com.eventplatform.attendee.domain.BookingCommandRepository;
import com.eventplatform.attendee.domain.BookingCommandStatus;
import com.eventplatform.attendee.domain.BookingLineItem;
import com.eventplatform.attendee.domain.BookingLineItemRepository;
import com.eventplatform.attendee.domain.BookingRepository;
import com.eventplatform.attendee.domain.BookingStatus;
import com.eventplatform.attendee.domain.BookingSaga;
import com.eventplatform.attendee.domain.BookingSagaRepository;
import com.eventplatform.attendee.domain.BookingSagaState;
import com.eventplatform.attendee.domain.Registration;
import com.eventplatform.attendee.domain.RegistrationRepository;
import com.eventplatform.attendee.domain.RegistrationStatus;
import com.eventplatform.attendee.domain.Ticket;
import com.eventplatform.attendee.domain.TicketHold;
import com.eventplatform.attendee.domain.TicketHoldRepository;
import com.eventplatform.attendee.domain.TicketHoldStatus;
import com.eventplatform.attendee.domain.TicketRepository;
import com.eventplatform.attendee.integration.AttendeeLifecycleEvents;
import com.eventplatform.attendee.outbox.AttendeeTransactionalOutbox;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingPersistenceService {
    private final BookingCommandRepository commandRepository;
    private final BookingRepository bookingRepository;
    private final RegistrationRepository registrationRepository;
    private final BookingLineItemRepository lineItemRepository;
    private final TicketHoldRepository holdRepository;
    private final TicketRepository ticketRepository;
    private final BookingSagaRepository sagaRepository;
    private final BookingResponseMapper responseMapper;
    private final AttendeeTransactionalOutbox outbox;
    private final Clock clock;

    public BookingPersistenceService(
            BookingCommandRepository commandRepository,
            BookingRepository bookingRepository,
            RegistrationRepository registrationRepository,
            BookingLineItemRepository lineItemRepository,
            TicketHoldRepository holdRepository,
            TicketRepository ticketRepository,
            BookingSagaRepository sagaRepository,
            BookingResponseMapper responseMapper,
            AttendeeTransactionalOutbox outbox,
            Clock clock) {
        this.commandRepository = commandRepository;
        this.bookingRepository = bookingRepository;
        this.registrationRepository = registrationRepository;
        this.lineItemRepository = lineItemRepository;
        this.holdRepository = holdRepository;
        this.ticketRepository = ticketRepository;
        this.sagaRepository = sagaRepository;
        this.responseMapper = responseMapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public AttendeeApi.BookingResponse complete(
            UUID commandId, EventInventoryPort.InventoryHold inventory, RequestContext context) {
        BookingCommand command = commandRepository.findByIdForUpdate(commandId)
                .orElseThrow(() -> new AttendeeApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "BOOKING_COMMAND_MISSING", "Booking command state is unavailable"));
        if (command.getStatus() == BookingCommandStatus.COMPLETED) {
            return responseMapper.booking(requiredBooking(command.getBookingId()));
        }
        validateInventory(command, inventory);
        Instant now = clock.instant();
        BigDecimal total = inventory.unitPrice().multiply(BigDecimal.valueOf(inventory.quantity()));
        UUID registrationId = UUID.randomUUID();
        RegistrationStatus registrationStatus = inventory.status() == EventInventoryPort.InventoryStatus.CONFIRMED
                ? RegistrationStatus.CONFIRMED
                : inventory.status() == EventInventoryPort.InventoryStatus.ACTIVE
                        ? RegistrationStatus.PENDING_PAYMENT : RegistrationStatus.EXPIRED;
        Registration registration = new Registration(
                registrationId, command.getAttendeeId(), command.getEventId(), registrationStatus, now);
        registrationRepository.save(registration);

        Booking booking = new Booking(
                command.getBookingId(), command.getAttendeeId(), registrationId, command.getEventId(),
                total, inventory.currency(), inventory.expiresAt(), now);
        if (inventory.status() == EventInventoryPort.InventoryStatus.CONFIRMED) {
            booking.confirm(now);
        } else if (inventory.status() == EventInventoryPort.InventoryStatus.ACTIVE) {
            booking.paymentPending(now);
        } else {
            booking.expire(now);
        }
        bookingRepository.save(booking);

        BookingLineItem lineItem = new BookingLineItem(
                UUID.randomUUID(), booking.getId(), inventory.eventId(), inventory.eventOrganizerId(),
                inventory.ticketTypeId(), inventory.eventTitle(), inventory.ticketTypeName(),
                inventory.eventStartsAt(), inventory.eventEndsAt(), inventory.venueId(), inventory.venueSpaceId(),
                inventory.unitPrice(), inventory.currency(), inventory.quantity(), now);
        lineItemRepository.save(lineItem);

        TicketHoldStatus localHoldStatus = switch (inventory.status()) {
            case ACTIVE -> TicketHoldStatus.ACTIVE;
            case CONFIRMED -> TicketHoldStatus.CONFIRMED;
            case RELEASED -> TicketHoldStatus.RELEASED;
            case EXPIRED -> TicketHoldStatus.EXPIRED;
        };
        TicketHold hold = new TicketHold(
                UUID.randomUUID(), booking.getId(), inventory.id(), inventory.eventId(), inventory.ticketTypeId(),
                inventory.quantity(), localHoldStatus, inventory.expiresAt(), inventory.confirmedAt(), now);
        holdRepository.save(hold);

        BookingSagaState initialSagaState = inventory.status() == EventInventoryPort.InventoryStatus.CONFIRMED
                ? BookingSagaState.CONFIRMED
                : inventory.status() == EventInventoryPort.InventoryStatus.ACTIVE
                        ? BookingSagaState.PAYMENT_PENDING : BookingSagaState.EXPIRED;
        sagaRepository.save(new BookingSaga(booking.getId(), initialSagaState, now));

        appendBookingCreated(booking, lineItem, hold, context, now);
        if (inventory.status() == EventInventoryPort.InventoryStatus.ACTIVE && total.signum() > 0) {
            outbox.append("Booking", booking.getId(), AttendeeLifecycleEvents.PAYMENT_REQUESTED,
                    AttendeeLifecycleEvents.VERSION,
                    new AttendeeLifecycleEvents.BookingPaymentRequestedV1(
                            booking.getId(), booking.getAttendeeId(), booking.getEventId(),
                            lineItem.getEventOrganizerId(), hold.getInventoryReservationId(),
                            lineItem.getTicketTypeId(), lineItem.getQuantity(), lineItem.getUnitPrice(),
                            booking.getTotalAmount(), booking.getCurrency(), lineItem.getEventStartsAt(),
                            hold.getExpiresAt(), now), context, now);
        }
        if (inventory.status() == EventInventoryPort.InventoryStatus.CONFIRMED) {
            issueTickets(booking, registration, lineItem, inventory.quantity(), context, now);
        } else if (inventory.status() == EventInventoryPort.InventoryStatus.EXPIRED
                || inventory.status() == EventInventoryPort.InventoryStatus.RELEASED) {
            appendHoldExpired(booking, hold, context, now);
        }
        command.complete(now);
        return responseMapper.booking(booking);
    }

    @Transactional(readOnly = true)
    public AttendeeApi.BookingResponse completed(UUID commandId, UUID attendeeId) {
        BookingCommand command = commandRepository.findById(commandId).orElseThrow();
        Booking booking = requiredBooking(command.getBookingId());
        requireOwner(booking, attendeeId);
        return responseMapper.booking(booking);
    }

    @Transactional
    public boolean expireByReservation(UUID reservationId, RequestContext context) {
        TicketHold hold = holdRepository.findByInventoryReservationIdForUpdate(reservationId).orElse(null);
        if (hold == null || hold.getStatus() != TicketHoldStatus.ACTIVE) return false;
        expire(hold, context, clock.instant());
        return true;
    }

    @Transactional
    public boolean expireByHoldId(UUID holdId, RequestContext context) {
        TicketHold hold = holdRepository.findByIdForUpdate(holdId).orElse(null);
        if (hold == null || hold.getStatus() != TicketHoldStatus.ACTIVE) return false;
        expire(hold, context, clock.instant());
        return true;
    }

    @Transactional
    public void cancelEvent(UUID eventId, RequestContext context) {
        Instant now = clock.instant();
        for (Booking booking : bookingRepository.findAllByEventIdForUpdate(eventId)) {
            if (booking.getStatus() == com.eventplatform.attendee.domain.BookingStatus.CANCELLED
                    || booking.getStatus() == com.eventplatform.attendee.domain.BookingStatus.REFUNDED) continue;
            booking.cancel(now);
            registrationRepository.findById(booking.getRegistrationId()).ifPresent(registration -> registration.cancel(now));
        }
        for (Ticket ticket : ticketRepository.findAllByEventIdForUpdate(eventId)) {
            if (ticket.getStatus() != com.eventplatform.attendee.domain.TicketStatus.CANCELLED
                    && ticket.getStatus() != com.eventplatform.attendee.domain.TicketStatus.REFUNDED) {
                ticket.cancel(now);
            }
        }
    }

    @Transactional
    public void markRefunded(UUID bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow(() -> new AttendeeApiException(
                HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "The booking was not found"));
        Instant now = clock.instant();
        booking.refund(now);
        registrationRepository.findById(booking.getRegistrationId()).ifPresent(registration -> registration.refund(now));
        ticketRepository.findAllByBookingIdForUpdate(bookingId).forEach(ticket -> ticket.refund(now));
    }

    private void expire(TicketHold hold, RequestContext context, Instant now) {
        hold.expire(now);
        Booking booking = bookingRepository.findByIdForUpdate(hold.getBookingId()).orElseThrow();
        if (booking.getStatus() != BookingStatus.CANCELLED && booking.getStatus() != BookingStatus.REFUNDED) {
            booking.expire(now);
            sagaRepository.findByIdForUpdate(booking.getId()).ifPresent(saga -> {
                if (saga.getState() == BookingSagaState.PAYMENT_PENDING) saga.expired(now);
            });
            registrationRepository.findById(booking.getRegistrationId())
                    .ifPresent(registration -> registration.expire(now));
            ticketRepository.findAllByBookingIdForUpdate(booking.getId()).forEach(ticket -> ticket.cancel(now));
        }
        appendHoldExpired(booking, hold, context, now);
    }

    private void issueTickets(
            Booking booking, Registration registration, BookingLineItem lineItem, int quantity,
            RequestContext context, Instant now) {
        for (int sequence = 0; sequence < quantity; sequence++) {
            Ticket ticket = new Ticket(UUID.randomUUID(), booking.getId(), registration.getId(), lineItem,
                    booking.getAttendeeId(), now);
            ticketRepository.save(ticket);
            outbox.append("Ticket", ticket.getId(), AttendeeLifecycleEvents.TICKET_ISSUED,
                    AttendeeLifecycleEvents.VERSION,
                    new AttendeeLifecycleEvents.TicketIssuedV1(
                            ticket.getId(), booking.getId(), booking.getAttendeeId(), booking.getEventId(),
                            ticket.getTicketTypeId(), now), context, now);
        }
    }

    private void appendBookingCreated(
            Booking booking, BookingLineItem lineItem, TicketHold hold, RequestContext context, Instant now) {
        outbox.append("Booking", booking.getId(), AttendeeLifecycleEvents.BOOKING_CREATED,
                AttendeeLifecycleEvents.VERSION,
                new AttendeeLifecycleEvents.BookingCreatedV1(
                        booking.getId(), booking.getAttendeeId(), booking.getEventId(), lineItem.getTicketTypeId(),
                        hold.getInventoryReservationId(), lineItem.getQuantity(), booking.getTotalAmount(),
                        booking.getCurrency(), booking.getStatus(), booking.getHoldExpiresAt(), now), context, now);
    }

    private void appendHoldExpired(Booking booking, TicketHold hold, RequestContext context, Instant now) {
        outbox.append("TicketHold", hold.getId(), AttendeeLifecycleEvents.TICKET_HOLD_EXPIRED,
                AttendeeLifecycleEvents.VERSION,
                new AttendeeLifecycleEvents.TicketHoldExpiredV1(
                        hold.getId(), booking.getId(), booking.getAttendeeId(), booking.getEventId(),
                        hold.getTicketTypeId(), hold.getInventoryReservationId(), hold.getQuantity(), now), context, now);
    }

    private void validateInventory(BookingCommand command, EventInventoryPort.InventoryHold inventory) {
        boolean invalid = inventory == null
                || !command.getEventId().equals(inventory.eventId())
                || !command.getTicketTypeId().equals(inventory.ticketTypeId())
                || !command.getAttendeeId().equals(inventory.requesterId())
                || command.getQuantity() != inventory.quantity()
                || inventory.eventOrganizerId() == null
                || inventory.eventTitle() == null
                || inventory.ticketTypeName() == null
                || inventory.eventStartsAt() == null
                || inventory.eventEndsAt() == null
                || !inventory.eventEndsAt().isAfter(inventory.eventStartsAt())
                || inventory.venueId() == null
                || inventory.unitPrice() == null
                || inventory.unitPrice().signum() < 0
                || inventory.currency() == null
                || !inventory.currency().matches("[A-Z]{3}")
                || inventory.expiresAt() == null;
        if (invalid) {
            throw new AttendeeApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INVENTORY_CONTRACT_MISMATCH",
                    "The event inventory response did not match the booking command");
        }
    }

    private Booking requiredBooking(UUID id) {
        return bookingRepository.findById(id).orElseThrow(() -> new AttendeeApiException(
                HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "The booking was not found"));
    }

    private void requireOwner(Booking booking, UUID attendeeId) {
        if (!booking.getAttendeeId().equals(attendeeId)) {
            throw new AttendeeApiException(HttpStatus.FORBIDDEN, "BOOKING_OWNERSHIP_REQUIRED",
                    "Only the booking owner may read this booking");
        }
    }
}
