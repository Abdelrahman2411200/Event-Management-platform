package com.eventplatform.attendee.integration;

import com.eventplatform.attendee.domain.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AttendeeLifecycleEvents {
    public static final int VERSION = 1;
    public static final String BOOKING_CREATED = "event-platform.booking.created.v1";
    public static final String TICKET_HOLD_EXPIRED = "event-platform.ticket-hold.expired.v1";
    public static final String TICKET_ISSUED = "event-platform.ticket.issued.v1";
    public static final String TICKET_CHECKED_IN = "event-platform.ticket.checked-in.v1";

    private AttendeeLifecycleEvents() {
    }

    public record BookingCreatedV1(
            UUID bookingId, UUID attendeeId, UUID eventId, UUID ticketTypeId,
            UUID inventoryReservationId, int quantity, BigDecimal totalAmount,
            String currency, BookingStatus status, Instant holdExpiresAt, Instant occurredAt) {
    }

    public record TicketHoldExpiredV1(
            UUID holdId, UUID bookingId, UUID attendeeId, UUID eventId,
            UUID ticketTypeId, UUID inventoryReservationId, int quantity, Instant expiredAt) {
    }

    public record TicketIssuedV1(
            UUID ticketId, UUID bookingId, UUID attendeeId, UUID eventId,
            UUID ticketTypeId, Instant issuedAt) {
    }

    public record TicketCheckedInV1(
            UUID ticketId, UUID bookingId, UUID attendeeId, UUID eventId,
            UUID scannerId, Instant checkedInAt) {
    }
}
