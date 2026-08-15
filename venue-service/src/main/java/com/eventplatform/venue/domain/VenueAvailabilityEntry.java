package com.eventplatform.venue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "venue_availability_entries")
public class VenueAvailabilityEntry {

    @Id
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(name = "venue_space_id")
    private UUID venueSpaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AvailabilityKind kind;

    @Column(name = "owner_reference", unique = true, length = 120)
    private String ownerReference;

    @Column(length = 500)
    private String reason;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "required_capacity", nullable = false)
    private int requiredCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AvailabilityStatus status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Version
    private long version;

    protected VenueAvailabilityEntry() {
    }

    public VenueAvailabilityEntry(
            UUID id,
            UUID venueId,
            UUID venueSpaceId,
            AvailabilityKind kind,
            String ownerReference,
            String reason,
            Instant startsAt,
            Instant endsAt,
            int requiredCapacity,
            UUID createdBy,
            Instant now) {
        this.id = id;
        this.venueId = venueId;
        this.venueSpaceId = venueSpaceId;
        this.kind = kind;
        this.ownerReference = ownerReference;
        this.reason = reason;
        // PostgreSQL persists timestamp values at microsecond precision. Normalize before the
        // first write so an idempotent replay compares the same canonical window both before
        // and after Hibernate reloads the entity (H2 may otherwise round nanoseconds).
        this.startsAt = startsAt.truncatedTo(ChronoUnit.MICROS);
        this.endsAt = endsAt.truncatedTo(ChronoUnit.MICROS);
        this.requiredCapacity = requiredCapacity;
        this.status = AvailabilityStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public boolean matches(UUID venueId, UUID spaceId, Instant startsAt, Instant endsAt, int capacity) {
        return this.venueId.equals(venueId)
                && java.util.Objects.equals(this.venueSpaceId, spaceId)
                && this.startsAt.truncatedTo(ChronoUnit.MICROS).equals(startsAt.truncatedTo(ChronoUnit.MICROS))
                && this.endsAt.truncatedTo(ChronoUnit.MICROS).equals(endsAt.truncatedTo(ChronoUnit.MICROS))
                && this.requiredCapacity == capacity;
    }

    public void release(Instant now) {
        if (status == AvailabilityStatus.ACTIVE) {
            status = AvailabilityStatus.RELEASED;
            releasedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public UUID getVenueSpaceId() {
        return venueSpaceId;
    }

    public AvailabilityKind getKind() {
        return kind;
    }

    public String getOwnerReference() {
        return ownerReference;
    }

    public String getReason() {
        return reason;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public int getRequiredCapacity() {
        return requiredCapacity;
    }

    public AvailabilityStatus getStatus() {
        return status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
