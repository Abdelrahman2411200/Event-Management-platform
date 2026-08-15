package com.eventplatform.event.application;

import java.time.Instant;
import java.util.UUID;

public interface VenueAvailabilityPort {

    Reservation reserve(
            UUID venueId,
            UUID venueSpaceId,
            Instant startsAt,
            Instant endsAt,
            int requiredCapacity,
            String ownerReference,
            String bearerToken);

    void release(UUID venueId, UUID reservationId, String bearerToken);

    record Reservation(UUID id, UUID venueId, UUID venueSpaceId, int requiredCapacity) {
    }
}
