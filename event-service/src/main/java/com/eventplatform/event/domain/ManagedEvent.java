package com.eventplatform.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "managed_events")
public class ManagedEvent {

    @Id
    private UUID id;

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false, length = 10000)
    private String description;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(name = "venue_space_id")
    private UUID venueSpaceId;

    @Column(name = "venue_reservation_id")
    private UUID venueReservationId;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EventStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ManagedEvent() {
    }

    public ManagedEvent(
            UUID id,
            UUID organizerId,
            String title,
            String description,
            UUID categoryId,
            String timezone,
            Instant startsAt,
            Instant endsAt,
            UUID venueId,
            UUID venueSpaceId,
            int capacity,
            Instant now) {
        this.id = id;
        this.organizerId = organizerId;
        apply(title, description, categoryId, timezone, startsAt, endsAt, venueId, venueSpaceId, capacity, now);
        this.status = EventStatus.DRAFT;
        this.createdAt = now;
    }

    public void update(
            String title,
            String description,
            UUID categoryId,
            String timezone,
            Instant startsAt,
            Instant endsAt,
            UUID venueId,
            UUID venueSpaceId,
            int capacity,
            Instant now) {
        apply(title, description, categoryId, timezone, startsAt, endsAt, venueId, venueSpaceId, capacity, now);
    }

    private void apply(
            String title,
            String description,
            UUID categoryId,
            String timezone,
            Instant startsAt,
            Instant endsAt,
            UUID venueId,
            UUID venueSpaceId,
            int capacity,
            Instant now) {
        this.title = title;
        this.description = description;
        this.categoryId = categoryId;
        this.timezone = timezone;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.venueId = venueId;
        this.venueSpaceId = venueSpaceId;
        this.capacity = capacity;
        this.updatedAt = now;
    }

    public void publish(UUID reservationId, Instant now) {
        status = EventStatus.PUBLISHED;
        venueReservationId = reservationId;
        publishedAt = now;
        updatedAt = now;
    }

    public void transitionTo(EventStatus target, Instant now) {
        status = target;
        if (target == EventStatus.CANCELLED) {
            cancelledAt = now;
        } else if (target == EventStatus.COMPLETED) {
            completedAt = now;
        } else if (target == EventStatus.ARCHIVED) {
            archivedAt = now;
        }
        updatedAt = now;
    }

    public boolean isTicketConfigurationMutable() {
        return status == EventStatus.DRAFT || status == EventStatus.PUBLISHED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public UUID getVenueSpaceId() {
        return venueSpaceId;
    }

    public UUID getVenueReservationId() {
        return venueReservationId;
    }

    public int getCapacity() {
        return capacity;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
