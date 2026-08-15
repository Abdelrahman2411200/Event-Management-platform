package com.eventplatform.venue.application;

import com.eventplatform.venue.api.VenueApi;
import com.eventplatform.venue.api.VenueApiException;
import com.eventplatform.venue.domain.Venue;
import com.eventplatform.venue.domain.VenueLocation;
import com.eventplatform.venue.domain.VenueRepository;
import com.eventplatform.venue.domain.VenueSpace;
import com.eventplatform.venue.domain.VenueSpaceRepository;
import com.eventplatform.venue.domain.VenueSpaceStatus;
import com.eventplatform.venue.domain.VenueStatus;
import com.eventplatform.venue.domain.VenueAvailabilityRepository;
import com.eventplatform.venue.security.AuthenticatedActor;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueManagementService {

    private final VenueRepository venueRepository;
    private final VenueSpaceRepository spaceRepository;
    private final VenueAvailabilityRepository availabilityRepository;
    private final LocationEnrichmentPort locationEnrichmentPort;

    public VenueManagementService(
            VenueRepository venueRepository,
            VenueSpaceRepository spaceRepository,
            VenueAvailabilityRepository availabilityRepository,
            LocationEnrichmentPort locationEnrichmentPort) {
        this.venueRepository = venueRepository;
        this.spaceRepository = spaceRepository;
        this.availabilityRepository = availabilityRepository;
        this.locationEnrichmentPort = locationEnrichmentPort;
    }

    @Transactional
    public VenueApi.VenueResponse create(VenueApi.CreateVenueRequest request, AuthenticatedActor actor) {
        Instant now = Instant.now();
        Venue venue = new Venue(
                UUID.randomUUID(),
                actor.userId(),
                clean(request.name()),
                nullableClean(request.description()),
                locationEnrichmentPort.enrich(toLocation(request.address())),
                validTimezone(request.timezone()),
                request.totalCapacity(),
                cleanSet(request.amenities()),
                cleanMap(request.metadata()),
                now);
        return toResponse(venueRepository.save(venue));
    }

    @Transactional(readOnly = true)
    public VenueApi.VenueResponse get(UUID venueId, AuthenticatedActor actor) {
        Venue venue = requiredVenue(venueId);
        if (venue.getStatus() == VenueStatus.ARCHIVED && !actor.isAdmin() && !actor.owns(venue.getOrganizerId())) {
            throw notFound();
        }
        return toResponse(venue);
    }

    @Transactional
    public VenueApi.VenueResponse update(
            UUID venueId,
            VenueApi.UpdateVenueRequest request,
            AuthenticatedActor actor) {
        Venue venue = requiredVenueForUpdate(venueId);
        requireOwner(venue, actor);
        requireActive(venue);
        int largestSpace = spaceRepository.findAllByVenueIdAndStatusOrderByNameAsc(
                        venueId, VenueSpaceStatus.ACTIVE).stream()
                .mapToInt(VenueSpace::getCapacity)
                .max()
                .orElse(0);
        if (request.totalCapacity() < largestSpace) {
            throw new VenueApiException(
                    HttpStatus.CONFLICT,
                    "VENUE_CAPACITY_BELOW_SPACE",
                    "Venue capacity cannot be lower than an active room capacity");
        }
        int reservedCapacity = Optional.ofNullable(
                        availabilityRepository.maximumActiveFutureWholeVenueCapacity(venueId, Instant.now()))
                .orElse(0);
        if (request.totalCapacity() < reservedCapacity) {
            throw new VenueApiException(
                    HttpStatus.CONFLICT,
                    "VENUE_CAPACITY_BELOW_RESERVATION",
                    "Venue capacity cannot be lower than an active future event assignment");
        }
        venue.update(
                clean(request.name()),
                nullableClean(request.description()),
                locationEnrichmentPort.enrich(toLocation(request.address())),
                validTimezone(request.timezone()),
                request.totalCapacity(),
                cleanSet(request.amenities()),
                cleanMap(request.metadata()),
                Instant.now());
        return toResponse(venue);
    }

    @Transactional
    public void archive(UUID venueId, AuthenticatedActor actor) {
        Venue venue = requiredVenueForUpdate(venueId);
        requireOwner(venue, actor);
        if (venue.getStatus() == VenueStatus.ARCHIVED) {
            return;
        }
        if (availabilityRepository.countActiveFutureReservations(venueId, Instant.now()) > 0) {
            throw new VenueApiException(
                    HttpStatus.CONFLICT,
                    "VENUE_HAS_ACTIVE_RESERVATIONS",
                    "Cancel future event assignments before archiving the venue");
        }
        venue.archive(Instant.now());
    }

    @Transactional
    public VenueApi.VenueSpaceResponse createSpace(
            UUID venueId,
            VenueApi.VenueSpaceRequest request,
            AuthenticatedActor actor) {
        Venue venue = requiredVenueForUpdate(venueId);
        requireOwner(venue, actor);
        requireActive(venue);
        validateSpaceCapacity(request.capacity(), venue.getTotalCapacity());
        String name = clean(request.name());
        if (spaceRepository.existsByVenueIdAndNameIgnoreCase(venueId, name)) {
            throw new VenueApiException(HttpStatus.CONFLICT, "VENUE_SPACE_EXISTS", "An active room has that name");
        }
        VenueSpace space = new VenueSpace(
                UUID.randomUUID(),
                venueId,
                name,
                nullableClean(request.description()),
                request.capacity(),
                cleanSet(request.amenities()),
                Instant.now());
        return toSpaceResponse(spaceRepository.save(space));
    }

    @Transactional
    public VenueApi.VenueSpaceResponse updateSpace(
            UUID venueId,
            UUID spaceId,
            VenueApi.VenueSpaceRequest request,
            AuthenticatedActor actor) {
        Venue venue = requiredVenueForUpdate(venueId);
        requireOwner(venue, actor);
        requireActive(venue);
        VenueSpace space = requiredSpace(spaceId, venueId);
        if (space.getStatus() == VenueSpaceStatus.ARCHIVED) {
            throw new VenueApiException(HttpStatus.CONFLICT, "VENUE_SPACE_ARCHIVED", "The room is archived");
        }
        validateSpaceCapacity(request.capacity(), venue.getTotalCapacity());
        int reservedCapacity = Optional.ofNullable(
                        availabilityRepository.maximumActiveFutureSpaceCapacity(spaceId, Instant.now()))
                .orElse(0);
        if (request.capacity() < reservedCapacity) {
            throw new VenueApiException(
                    HttpStatus.CONFLICT,
                    "VENUE_SPACE_CAPACITY_BELOW_RESERVATION",
                    "Room capacity cannot be lower than an active future event assignment");
        }
        String name = clean(request.name());
        if (!space.getName().equalsIgnoreCase(name)
                && spaceRepository.existsByVenueIdAndNameIgnoreCase(venueId, name)) {
            throw new VenueApiException(HttpStatus.CONFLICT, "VENUE_SPACE_EXISTS", "An active room has that name");
        }
        space.update(
                name,
                nullableClean(request.description()),
                request.capacity(),
                cleanSet(request.amenities()),
                Instant.now());
        return toSpaceResponse(space);
    }

    @Transactional
    public void archiveSpace(UUID venueId, UUID spaceId, AuthenticatedActor actor) {
        Venue venue = requiredVenueForUpdate(venueId);
        requireOwner(venue, actor);
        VenueSpace space = requiredSpace(spaceId, venueId);
        if (space.getStatus() == VenueSpaceStatus.ARCHIVED) {
            return;
        }
        if (availabilityRepository.countActiveFutureEntriesForSpace(spaceId, Instant.now()) > 0) {
            throw new VenueApiException(
                    HttpStatus.CONFLICT,
                    "VENUE_SPACE_HAS_ACTIVE_AVAILABILITY",
                    "Release future reservations or blocks before archiving the room");
        }
        space.archive(Instant.now());
    }

    Venue requiredVenue(UUID venueId) {
        return venueRepository.findById(venueId).orElseThrow(this::notFound);
    }

    Venue requiredVenueForUpdate(UUID venueId) {
        return venueRepository.findByIdForUpdate(venueId).orElseThrow(this::notFound);
    }

    VenueSpace requiredSpace(UUID spaceId, UUID venueId) {
        return spaceRepository.findByIdAndVenueId(spaceId, venueId).orElseThrow(() -> new VenueApiException(
                HttpStatus.NOT_FOUND, "VENUE_SPACE_NOT_FOUND", "The venue room was not found"));
    }

    void requireOwner(Venue venue, AuthenticatedActor actor) {
        if (!actor.isAdmin() && !actor.owns(venue.getOrganizerId())) {
            throw new VenueApiException(HttpStatus.FORBIDDEN, "VENUE_OWNERSHIP_REQUIRED", "Venue ownership is required");
        }
    }

    void requireActive(Venue venue) {
        if (venue.getStatus() != VenueStatus.ACTIVE) {
            throw new VenueApiException(HttpStatus.CONFLICT, "VENUE_ARCHIVED", "The venue is archived");
        }
    }

    private VenueLocation toLocation(VenueApi.AddressRequest address) {
        boolean onlyOneCoordinate = (address.latitude() == null) != (address.longitude() == null);
        if (onlyOneCoordinate) {
            throw new VenueApiException(
                    HttpStatus.BAD_REQUEST,
                    "LOCATION_COORDINATES_INCOMPLETE",
                    "Latitude and longitude must be supplied together");
        }
        return new VenueLocation(
                clean(address.addressLine1()),
                nullableClean(address.addressLine2()),
                clean(address.city()),
                nullableClean(address.region()),
                nullableClean(address.postalCode()),
                address.countryCode().trim().toUpperCase(Locale.ROOT),
                address.latitude(),
                address.longitude());
    }

    private String validTimezone(String candidate) {
        try {
            return ZoneId.of(candidate.trim()).getId();
        } catch (ZoneRulesException exception) {
            throw new VenueApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", "Timezone must be a valid IANA zone");
        }
    }

    private void validateSpaceCapacity(int capacity, int venueCapacity) {
        if (capacity > venueCapacity) {
            throw new VenueApiException(
                    HttpStatus.BAD_REQUEST,
                    "SPACE_CAPACITY_EXCEEDS_VENUE",
                    "Room capacity cannot exceed venue capacity");
        }
    }

    private VenueApi.VenueResponse toResponse(Venue venue) {
        VenueLocation location = venue.getLocation();
        return new VenueApi.VenueResponse(
                venue.getId(),
                venue.getOrganizerId(),
                venue.getName(),
                venue.getDescription(),
                new VenueApi.AddressResponse(
                        location.addressLine1(),
                        location.addressLine2(),
                        location.city(),
                        location.region(),
                        location.postalCode(),
                        location.countryCode(),
                        location.latitude(),
                        location.longitude()),
                venue.getTimezone(),
                venue.getTotalCapacity(),
                venue.getStatus(),
                venue.getAmenities(),
                venue.getMetadata(),
                spaceRepository.findAllByVenueIdOrderByNameAsc(venue.getId()).stream()
                        .map(this::toSpaceResponse)
                        .toList(),
                venue.getCreatedAt(),
                venue.getUpdatedAt());
    }

    private VenueApi.VenueSpaceResponse toSpaceResponse(VenueSpace space) {
        return new VenueApi.VenueSpaceResponse(
                space.getId(),
                space.getVenueId(),
                space.getName(),
                space.getDescription(),
                space.getCapacity(),
                space.getStatus(),
                space.getAmenities(),
                space.getCreatedAt(),
                space.getUpdatedAt());
    }

    private VenueApiException notFound() {
        return new VenueApiException(HttpStatus.NOT_FOUND, "VENUE_NOT_FOUND", "The venue was not found");
    }

    private String clean(String value) {
        return value.trim();
    }

    private String nullableClean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Set<String> cleanSet(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream().map(String::trim).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Map<String, String> cleanMap(Map<String, String> values) {
        if (values == null) {
            return Map.of();
        }
        return values.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                entry -> entry.getKey().trim(), entry -> entry.getValue().trim()));
    }
}
