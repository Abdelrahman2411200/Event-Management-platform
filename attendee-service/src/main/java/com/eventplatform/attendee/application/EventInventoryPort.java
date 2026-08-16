package com.eventplatform.attendee.application;

import com.eventplatform.attendee.api.RequestContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface EventInventoryPort {
    InventoryHold reserve(
            UUID eventId, UUID ticketTypeId, int quantity, String idempotencyKey, RequestContext context);

    InventoryHold confirm(
            UUID eventId, UUID ticketTypeId, UUID reservationId, String idempotencyKey, RequestContext context);

    InventoryHold release(
            UUID eventId, UUID ticketTypeId, UUID reservationId, String idempotencyKey, RequestContext context);

    enum InventoryStatus { ACTIVE, CONFIRMED, RELEASED, EXPIRED }

    record InventoryHold(
            UUID id,
            UUID eventId,
            UUID eventOrganizerId,
            String eventTitle,
            Instant eventStartsAt,
            Instant eventEndsAt,
            UUID venueId,
            UUID venueSpaceId,
            UUID ticketTypeId,
            String ticketTypeName,
            BigDecimal unitPrice,
            String currency,
            UUID requesterId,
            int quantity,
            InventoryStatus status,
            Instant expiresAt,
            Instant confirmedAt,
            int remainingQuantity,
            Instant createdAt,
            Instant updatedAt) {
    }
}
