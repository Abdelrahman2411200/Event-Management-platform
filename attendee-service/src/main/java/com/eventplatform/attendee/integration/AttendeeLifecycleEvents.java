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
    public static final String PAYMENT_REQUESTED = "event-platform.booking.payment-requested.v1";
    public static final String INVENTORY_CONFIRMATION_REQUESTED = "event-platform.inventory.confirmation-requested.v1";
    public static final String INVENTORY_RELEASE_REQUESTED = "event-platform.inventory.release-requested.v1";
    public static final String PAYMENT_COMPENSATION_REQUESTED = "event-platform.payment.compensation-requested.v1";
    public static final String BOOKING_CONFIRMED = "event-platform.booking.confirmed.v1";
    public static final String BOOKING_PAYMENT_FAILED = "event-platform.booking.payment-failed.v1";
    public static final String BOOKING_REFUNDED = "event-platform.booking.refunded.v1";

    private AttendeeLifecycleEvents() {
    }

    public record BookingCreatedV1(
            UUID bookingId, UUID attendeeId, String attendeeEmail, String attendeePhone,
            String attendeeLocale, String attendeeDisplayName, UUID eventId, UUID ticketTypeId,
            UUID inventoryReservationId, int quantity, BigDecimal totalAmount,
            String currency, BookingStatus status, String eventTitle, String ticketTypeName,
            Instant eventStartsAt, Instant holdExpiresAt, Instant occurredAt) {
    }

    public record TicketHoldExpiredV1(
            UUID holdId, UUID bookingId, UUID attendeeId, UUID eventId,
            UUID ticketTypeId, UUID inventoryReservationId, int quantity, Instant expiredAt) {
    }

    public record TicketIssuedV1(
            UUID ticketId, UUID bookingId, UUID attendeeId, UUID eventId,
            UUID ticketTypeId, String eventTitle, String ticketTypeName, Instant eventStartsAt,
            String attendeeEmail, String attendeePhone, String attendeeLocale,
            String qrToken, Instant issuedAt) {
    }

    public record TicketCheckedInV1(
            UUID ticketId, UUID bookingId, UUID attendeeId, UUID eventId,
            UUID scannerId, Instant checkedInAt) {
    }

    public record BookingPaymentRequestedV1(
            UUID bookingId, UUID attendeeId, UUID eventId, UUID eventOrganizerId,
            UUID inventoryReservationId, UUID ticketTypeId, int quantity,
            BigDecimal unitPrice, BigDecimal totalAmount, String currency,
            String eventTitle, String attendeeEmail, String attendeePhone, String attendeeLocale,
            Instant eventStartsAt, Instant holdExpiresAt, Instant occurredAt) {
    }

    public record InventorySagaCommandV1(
            UUID bookingId, UUID paymentId, UUID attendeeId, UUID eventId,
            UUID ticketTypeId, UUID inventoryReservationId, int quantity,
            String commandKey, Instant occurredAt) {
    }

    public record PaymentCompensationRequestedV1(
            UUID bookingId, UUID paymentId, UUID attendeeId, String reason, Instant occurredAt) {
    }

    public record BookingSagaChangedV1(
            UUID bookingId, UUID paymentId, UUID attendeeId, UUID eventId,
            BookingStatus status, String failureCode, Instant occurredAt) {
    }

    public record BookingRefundedV1(
            UUID bookingId, UUID paymentId, UUID attendeeId, UUID eventId,
            BigDecimal amount, String currency, java.util.List<UUID> ticketIds,
            boolean full, Instant occurredAt) {
    }
}
