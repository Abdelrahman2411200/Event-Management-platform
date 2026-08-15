package com.eventplatform.event.integration;

import com.eventplatform.event.domain.EventStatus;
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
}
