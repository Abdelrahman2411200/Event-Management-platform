package com.eventplatform.venue.application;

import com.eventplatform.venue.api.VenueApi;
import com.eventplatform.venue.api.VenueApiException;
import com.eventplatform.venue.domain.AvailabilityKind;
import com.eventplatform.venue.domain.AvailabilityStatus;
import com.eventplatform.venue.domain.Venue;
import com.eventplatform.venue.domain.VenueAvailabilityEntry;
import com.eventplatform.venue.domain.VenueAvailabilityRepository;
import com.eventplatform.venue.domain.VenueSpace;
import com.eventplatform.venue.domain.VenueSpaceStatus;
import com.eventplatform.venue.security.AuthenticatedActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueAvailabilityService {

    private final VenueManagementService venueManagementService;
    private final VenueAvailabilityRepository availabilityRepository;

    public VenueAvailabilityService(
            VenueManagementService venueManagementService,
            VenueAvailabilityRepository availabilityRepository) {
        this.venueManagementService = venueManagementService;
        this.availabilityRepository = availabilityRepository;
    }

    @Transactional(readOnly = true)
    public VenueApi.AvailabilityCheckResponse check(
            UUID venueId,
            VenueApi.AvailabilityCheckRequest request) {
        Venue venue = venueManagementService.requiredVenue(venueId);
        validateWindow(request.startsAt(), request.endsAt());
        Target target = target(venue, request.venueSpaceId(), request.requiredCapacity());
        List<VenueAvailabilityEntry> conflicts = conflicts(
                venueId, request.venueSpaceId(), request.startsAt(), request.endsAt(), null);
        return checkResponse(venue, target, conflicts);
    }

    @Transactional
    public VenueApi.AvailabilityEntryResponse createBlock(
            UUID venueId,
            VenueApi.AvailabilityBlockRequest request,
            AuthenticatedActor actor) {
        Venue venue = venueManagementService.requiredVenueForUpdate(venueId);
        venueManagementService.requireOwner(venue, actor);
        venueManagementService.requireActive(venue);
        validateWindow(request.startsAt(), request.endsAt());
        Target target = target(venue, request.venueSpaceId(), 1);
        List<VenueAvailabilityEntry> conflicts = conflicts(
                venueId, request.venueSpaceId(), request.startsAt(), request.endsAt(), null);
        if (!conflicts.isEmpty()) {
            throw unavailable();
        }
        VenueAvailabilityEntry entry = new VenueAvailabilityEntry(
                UUID.randomUUID(),
                venueId,
                request.venueSpaceId(),
                AvailabilityKind.BLOCK,
                null,
                request.reason().trim(),
                request.startsAt(),
                request.endsAt(),
                target.capacity(),
                actor.userId(),
                Instant.now());
        return toResponse(availabilityRepository.save(entry));
    }

    @Transactional
    public VenueApi.AvailabilityEntryResponse reserve(
            UUID venueId,
            VenueApi.AvailabilityReservationRequest request,
            AuthenticatedActor actor) {
        Venue venue = venueManagementService.requiredVenueForUpdate(venueId);
        venueManagementService.requireOwner(venue, actor);
        venueManagementService.requireActive(venue);
        validateWindow(request.startsAt(), request.endsAt());
        target(venue, request.venueSpaceId(), request.requiredCapacity());

        String ownerReference = request.ownerReference().trim();
        VenueAvailabilityEntry existing = availabilityRepository.findByOwnerReference(ownerReference).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == AvailabilityStatus.ACTIVE
                    && existing.matches(
                            venueId,
                            request.venueSpaceId(),
                            request.startsAt(),
                            request.endsAt(),
                            request.requiredCapacity())) {
                return toResponse(existing);
            }
            throw new VenueApiException(
                    HttpStatus.CONFLICT,
                    "VENUE_RESERVATION_IDEMPOTENCY_CONFLICT",
                    "The owner reference is already bound to another venue reservation");
        }

        if (!conflicts(
                venueId,
                request.venueSpaceId(),
                request.startsAt(),
                request.endsAt(),
                ownerReference).isEmpty()) {
            throw unavailable();
        }
        VenueAvailabilityEntry entry = new VenueAvailabilityEntry(
                UUID.randomUUID(),
                venueId,
                request.venueSpaceId(),
                AvailabilityKind.EVENT_RESERVATION,
                ownerReference,
                "Event assignment",
                request.startsAt(),
                request.endsAt(),
                request.requiredCapacity(),
                actor.userId(),
                Instant.now());
        return toResponse(availabilityRepository.save(entry));
    }

    @Transactional
    public VenueApi.AvailabilityEntryResponse release(
            UUID venueId,
            UUID entryId,
            AuthenticatedActor actor,
            AvailabilityKind expectedKind) {
        Venue venue = venueManagementService.requiredVenueForUpdate(venueId);
        venueManagementService.requireOwner(venue, actor);
        VenueAvailabilityEntry entry = availabilityRepository.findByIdAndVenueId(entryId, venueId)
                .orElseThrow(() -> new VenueApiException(
                        HttpStatus.NOT_FOUND,
                        "AVAILABILITY_ENTRY_NOT_FOUND",
                        "The availability entry was not found"));
        if (entry.getKind() != expectedKind) {
            throw new VenueApiException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_ENTRY_KIND_MISMATCH",
                    "The availability entry has a different type");
        }
        entry.release(Instant.now());
        return toResponse(entry);
    }

    private Target target(Venue venue, UUID spaceId, int requiredCapacity) {
        venueManagementService.requireActive(venue);
        int capacity;
        if (spaceId == null) {
            capacity = venue.getTotalCapacity();
        } else {
            VenueSpace space = venueManagementService.requiredSpace(spaceId, venue.getId());
            if (space.getStatus() != VenueSpaceStatus.ACTIVE) {
                throw new VenueApiException(HttpStatus.CONFLICT, "VENUE_SPACE_ARCHIVED", "The venue room is archived");
            }
            capacity = space.getCapacity();
        }
        if (requiredCapacity > capacity) {
            throw new VenueApiException(
                    HttpStatus.CONFLICT,
                    "VENUE_CAPACITY_EXCEEDED",
                    "Required event capacity exceeds the selected venue capacity");
        }
        return new Target(spaceId, capacity);
    }

    private List<VenueAvailabilityEntry> conflicts(
            UUID venueId,
            UUID spaceId,
            Instant startsAt,
            Instant endsAt,
            String excludedOwner) {
        return spaceId == null
                ? availabilityRepository.findWholeVenueConflicts(venueId, startsAt, endsAt, excludedOwner)
                : availabilityRepository.findSpaceConflicts(venueId, spaceId, startsAt, endsAt, excludedOwner);
    }

    private void validateWindow(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new VenueApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_AVAILABILITY_WINDOW",
                    "Availability end must be after its start");
        }
        if (!endsAt.isAfter(Instant.now())) {
            throw new VenueApiException(
                    HttpStatus.BAD_REQUEST,
                    "AVAILABILITY_WINDOW_IN_PAST",
                    "Availability end must be in the future");
        }
    }

    private VenueApi.AvailabilityCheckResponse checkResponse(
            Venue venue,
            Target target,
            List<VenueAvailabilityEntry> conflicts) {
        return new VenueApi.AvailabilityCheckResponse(
                venue.getId(),
                target.spaceId(),
                true,
                target.capacity(),
                conflicts.isEmpty(),
                conflicts.stream()
                        .map(entry -> new VenueApi.AvailabilityConflictResponse(
                                entry.getId(),
                                entry.getVenueSpaceId(),
                                entry.getKind(),
                                entry.getStartsAt(),
                                entry.getEndsAt()))
                        .toList());
    }

    private VenueApi.AvailabilityEntryResponse toResponse(VenueAvailabilityEntry entry) {
        return new VenueApi.AvailabilityEntryResponse(
                entry.getId(),
                entry.getVenueId(),
                entry.getVenueSpaceId(),
                entry.getKind(),
                entry.getOwnerReference(),
                entry.getReason(),
                entry.getStartsAt(),
                entry.getEndsAt(),
                entry.getRequiredCapacity(),
                entry.getStatus(),
                entry.getCreatedAt());
    }

    private VenueApiException unavailable() {
        return new VenueApiException(
                HttpStatus.CONFLICT,
                "VENUE_UNAVAILABLE",
                "The venue or room is unavailable during the requested time window");
    }

    private record Target(UUID spaceId, int capacity) {
    }
}
