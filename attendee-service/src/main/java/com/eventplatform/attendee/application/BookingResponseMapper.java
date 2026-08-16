package com.eventplatform.attendee.application;

import com.eventplatform.attendee.api.AttendeeApi;
import com.eventplatform.attendee.api.AttendeeApiException;
import com.eventplatform.attendee.domain.Booking;
import com.eventplatform.attendee.domain.BookingLineItem;
import com.eventplatform.attendee.domain.BookingLineItemRepository;
import com.eventplatform.attendee.domain.Registration;
import com.eventplatform.attendee.domain.RegistrationRepository;
import com.eventplatform.attendee.domain.Ticket;
import com.eventplatform.attendee.domain.TicketHold;
import com.eventplatform.attendee.domain.TicketHoldRepository;
import com.eventplatform.attendee.domain.TicketRepository;
import com.eventplatform.attendee.domain.TicketStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class BookingResponseMapper {
    private final RegistrationRepository registrationRepository;
    private final BookingLineItemRepository lineItemRepository;
    private final TicketHoldRepository holdRepository;
    private final TicketRepository ticketRepository;
    private final QrTokenService qrTokenService;

    public BookingResponseMapper(
            RegistrationRepository registrationRepository,
            BookingLineItemRepository lineItemRepository,
            TicketHoldRepository holdRepository,
            TicketRepository ticketRepository,
            QrTokenService qrTokenService) {
        this.registrationRepository = registrationRepository;
        this.lineItemRepository = lineItemRepository;
        this.holdRepository = holdRepository;
        this.ticketRepository = ticketRepository;
        this.qrTokenService = qrTokenService;
    }

    public AttendeeApi.BookingResponse booking(Booking booking) {
        Registration registration = registrationRepository.findById(booking.getRegistrationId())
                .orElseThrow(() -> new AttendeeApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "REGISTRATION_MISSING", "Booking registration state is unavailable"));
        TicketHold hold = holdRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new AttendeeApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "TICKET_HOLD_MISSING", "Booking hold state is unavailable"));
        return new AttendeeApi.BookingResponse(
                booking.getId(), booking.getAttendeeId(), booking.getRegistrationId(), booking.getEventId(),
                registration.getStatus(), booking.getStatus(), booking.getTotalAmount(), booking.getCurrency(),
                booking.getHoldExpiresAt(),
                lineItemRepository.findAllByBookingId(booking.getId()).stream().map(this::lineItem).toList(),
                hold(hold),
                ticketRepository.findAllByBookingIdOrderByIssuedAt(booking.getId()).stream().map(this::ticket).toList(),
                booking.getCreatedAt(), booking.getUpdatedAt());
    }

    public AttendeeApi.TicketResponse ticket(Ticket ticket) {
        String token = ticket.getStatus() == TicketStatus.CANCELLED || ticket.getStatus() == TicketStatus.REFUNDED
                ? null : qrTokenService.issue(ticket);
        return new AttendeeApi.TicketResponse(
                ticket.getId(), ticket.getBookingId(), ticket.getRegistrationId(), ticket.getEventId(),
                ticket.getTicketTypeId(), ticket.getEventTitle(), ticket.getTicketTypeName(),
                ticket.getEventStartsAt(), ticket.getEventEndsAt(), ticket.getVenueId(), ticket.getVenueSpaceId(),
                ticket.getStatus(), ticket.getIssuedAt(), ticket.getCheckedInAt(), token);
    }

    private AttendeeApi.BookingLineItemResponse lineItem(BookingLineItem item) {
        return new AttendeeApi.BookingLineItemResponse(
                item.getId(), item.getEventId(), item.getTicketTypeId(), item.getEventTitle(), item.getTicketTypeName(),
                item.getEventStartsAt(), item.getEventEndsAt(), item.getVenueId(), item.getVenueSpaceId(),
                item.getUnitPrice(), item.getCurrency(), item.getQuantity(), item.getLineTotal());
    }

    private AttendeeApi.TicketHoldResponse hold(TicketHold hold) {
        return new AttendeeApi.TicketHoldResponse(
                hold.getId(), hold.getInventoryReservationId(), hold.getStatus(), hold.getQuantity(),
                hold.getExpiresAt(), hold.getConfirmedAt(), hold.getReleasedAt());
    }
}
