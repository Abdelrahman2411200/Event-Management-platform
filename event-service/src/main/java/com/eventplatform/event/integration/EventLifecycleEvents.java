package com.eventplatform.event.integration;

import com.eventplatform.event.domain.EventStatus;
import com.eventplatform.event.domain.InventoryReservationStatus;
import com.eventplatform.event.domain.TicketTypeStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class EventLifecycleEvents {

    public static final int VERSION = 1;
    public static final String EVENT_PUBLISHED = "event-platform.event.published.v1";
    public static final String EVENT_UPDATED = "event-platform.event.updated.v1";
    public static final String EVENT_CANCELLED = "event-platform.event.cancelled.v1";
    public static final String TICKET_TYPE_CHANGED = "event-platform.ticket-type.changed.v1";
    public static final String INVENTORY_HELD = "event-platform.inventory.held.v1";
    public static final String INVENTORY_CONFIRMED = "event-platform.inventory.confirmed.v1";
    public static final String INVENTORY_RELEASED = "event-platform.inventory.released.v1";
    public static final String INVENTORY_EXPIRED = "event-platform.inventory.expired.v1";
    public static final String INVENTORY_CONFIRMATION_REJECTED = "event-platform.inventory.confirmation-rejected.v1";
    public static final String INVENTORY_RELEASE_REJECTED = "event-platform.inventory.release-rejected.v1";

    private EventLifecycleEvents() {
    }

    public record EventPublishedV1(
            UUID eventId,
            UUID organizerId,
            UUID categoryId,
            UUID venueId,
            UUID venueSpaceId,
            Instant startsAt,
            Instant endsAt,
            int capacity,
            EventStatus status,
            Instant occurredAt) {
    }

    public record EventUpdatedV1(
            UUID eventId,
            UUID organizerId,
            UUID categoryId,
            UUID venueId,
            UUID venueSpaceId,
            Instant startsAt,
            Instant endsAt,
            int capacity,
            EventStatus status,
            Instant occurredAt) {
    }

    public record EventCancelledV1(
            UUID eventId,
            UUID organizerId,
            EventStatus previousStatus,
            Instant cancelledAt) {
    }

    public record TicketTypeChangedV1(
            UUID eventId,
            UUID ticketTypeId,
            String name,
            BigDecimal price,
            String currency,
            int allocation,
            Instant salesStart,
            Instant salesEnd,
            TicketTypeStatus status,
            Instant occurredAt) {
    }

    public record InventoryReservationChangedV1(
            UUID reservationId,
            UUID eventId,
            UUID ticketTypeId,
            UUID requesterId,
            int quantity,
            InventoryReservationStatus status,
            Instant expiresAt,
            Instant occurredAt) {
    }

    public record InventorySagaRejectedV1(
            UUID bookingId, UUID paymentId, UUID reservationId, UUID eventId,
            UUID ticketTypeId, String commandKey, String failureCode,
            String failureReason, Instant occurredAt) {
    }
}
