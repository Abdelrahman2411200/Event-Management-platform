package com.eventplatform.venue.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "venue_spaces")
public class VenueSpace {

    @Id
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VenueSpaceStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "venue_space_amenities", joinColumns = @JoinColumn(name = "venue_space_id"))
    @Column(name = "amenity", nullable = false, length = 100)
    private Set<String> amenities = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    private long version;

    protected VenueSpace() {
    }

    public VenueSpace(
            UUID id,
            UUID venueId,
            String name,
            String description,
            int capacity,
            Set<String> amenities,
            Instant now) {
        this.id = id;
        this.venueId = venueId;
        this.name = name;
        this.description = description;
        this.capacity = capacity;
        this.amenities.addAll(amenities);
        this.status = VenueSpaceStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String description, int capacity, Set<String> amenities, Instant now) {
        this.name = name;
        this.description = description;
        this.capacity = capacity;
        this.amenities.clear();
        this.amenities.addAll(amenities);
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        status = VenueSpaceStatus.ARCHIVED;
        archivedAt = now;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getCapacity() {
        return capacity;
    }

    public VenueSpaceStatus getStatus() {
        return status;
    }

    public Set<String> getAmenities() {
        return Set.copyOf(amenities);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
