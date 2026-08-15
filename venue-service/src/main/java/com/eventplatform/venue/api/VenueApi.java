package com.eventplatform.venue.api;

import com.eventplatform.venue.domain.AvailabilityKind;
import com.eventplatform.venue.domain.AvailabilityStatus;
import com.eventplatform.venue.domain.VenueSpaceStatus;
import com.eventplatform.venue.domain.VenueStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VenueApi {

    private VenueApi() {
    }

    public record AddressRequest(
            @NotBlank @Size(max = 250) String addressLine1,
            @Size(max = 250) String addressLine2,
            @NotBlank @Size(max = 120) String city,
            @Size(max = 120) String region,
            @Size(max = 32) String postalCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude) {
    }

    public record CreateVenueRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 4000) String description,
            @NotNull @Valid AddressRequest address,
            @NotBlank @Size(max = 64) String timezone,
            @Min(1) @Max(10_000_000) int totalCapacity,
            @Size(max = 100) Set<@NotBlank @Size(max = 100) String> amenities,
            @Size(max = 100) Map<@NotBlank @Size(max = 100) String, @NotBlank @Size(max = 500) String> metadata) {
    }

    public record UpdateVenueRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 4000) String description,
            @NotNull @Valid AddressRequest address,
            @NotBlank @Size(max = 64) String timezone,
            @Min(1) @Max(10_000_000) int totalCapacity,
            @Size(max = 100) Set<@NotBlank @Size(max = 100) String> amenities,
            @Size(max = 100) Map<@NotBlank @Size(max = 100) String, @NotBlank @Size(max = 500) String> metadata) {
    }

    public record AddressResponse(
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude) {
    }

    public record VenueResponse(
            UUID id,
            UUID organizerId,
            String name,
            String description,
            AddressResponse address,
            String timezone,
            int totalCapacity,
            VenueStatus status,
            Set<String> amenities,
            Map<String, String> metadata,
            List<VenueSpaceResponse> spaces,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record VenueSpaceRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @Min(1) @Max(10_000_000) int capacity,
            @Size(max = 100) Set<@NotBlank @Size(max = 100) String> amenities) {
    }

    public record VenueSpaceResponse(
            UUID id,
            UUID venueId,
            String name,
            String description,
            int capacity,
            VenueSpaceStatus status,
            Set<String> amenities,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record AvailabilityCheckRequest(
            UUID venueSpaceId,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @Min(1) @Max(10_000_000) int requiredCapacity) {
    }

    public record AvailabilityConflictResponse(
            UUID id,
            UUID venueSpaceId,
            AvailabilityKind kind,
            Instant startsAt,
            Instant endsAt) {
    }

    public record AvailabilityCheckResponse(
            UUID venueId,
            UUID venueSpaceId,
            boolean active,
            int capacity,
            boolean available,
            List<AvailabilityConflictResponse> conflicts) {
    }

    public record AvailabilityBlockRequest(
            UUID venueSpaceId,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record AvailabilityReservationRequest(
            UUID venueSpaceId,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @Min(1) @Max(10_000_000) int requiredCapacity,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,120}") String ownerReference) {
    }

    public record AvailabilityEntryResponse(
            UUID id,
            UUID venueId,
            UUID venueSpaceId,
            AvailabilityKind kind,
            String ownerReference,
            String reason,
            Instant startsAt,
            Instant endsAt,
            int requiredCapacity,
            AvailabilityStatus status,
            Instant createdAt) {
    }
}
