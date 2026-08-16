package com.eventplatform.attendee.api;

import com.eventplatform.attendee.domain.BookingStatus;
import com.eventplatform.attendee.domain.RegistrationStatus;
import com.eventplatform.attendee.domain.ScanOutcome;
import com.eventplatform.attendee.domain.TicketHoldStatus;
import com.eventplatform.attendee.domain.TicketStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AttendeeApi {
    private AttendeeApi() {
    }

    public record ProfileRequest(
            @Size(max = 160) String displayName,
            @Pattern(regexp = "^$|^[+0-9() .-]{7,32}$") String phoneNumber,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*") @Size(max = 16) String locale) {
    }

    public record ProfileResponse(
            UUID id, String displayName, String phoneNumber, String locale, Instant createdAt, Instant updatedAt) {
    }

    public record CreateBookingRequest(
            @NotNull UUID eventId,
            @NotNull UUID ticketTypeId,
            @Min(1) @Max(100) int quantity) {
    }

    public record BookingLineItemResponse(
            UUID id, UUID eventId, UUID ticketTypeId, String eventTitle, String ticketTypeName,
            Instant eventStartsAt, Instant eventEndsAt, UUID venueId, UUID venueSpaceId,
            BigDecimal unitPrice, String currency, int quantity, BigDecimal lineTotal) {
    }

    public record TicketHoldResponse(
            UUID id, UUID inventoryReservationId, TicketHoldStatus status, int quantity,
            Instant expiresAt, Instant confirmedAt, Instant releasedAt) {
    }

    public record TicketResponse(
            UUID id, UUID bookingId, UUID registrationId, UUID eventId, UUID ticketTypeId,
            String eventTitle, String ticketTypeName, Instant eventStartsAt, Instant eventEndsAt,
            UUID venueId, UUID venueSpaceId, TicketStatus status, Instant issuedAt,
            Instant checkedInAt, String qrToken) {
    }

    public record BookingResponse(
            UUID id, UUID attendeeId, UUID registrationId, UUID eventId,
            RegistrationStatus registrationStatus, BookingStatus status,
            BigDecimal totalAmount, String currency, Instant holdExpiresAt,
            List<BookingLineItemResponse> lineItems, TicketHoldResponse hold,
            List<TicketResponse> tickets, Instant createdAt, Instant updatedAt) {
    }

    public record TicketListResponse(List<TicketResponse> tickets) {
    }

    public record ScanRequest(@NotNull UUID eventId, @NotBlank @Size(max = 2048) String qrToken) {
    }

    public record ScanResponse(
            boolean accepted, ScanOutcome outcome, UUID eventId, UUID ticketId,
            TicketStatus ticketStatus, Instant checkedInAt, Instant attemptedAt) {
    }
}
