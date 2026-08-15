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
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "venues")
public class Venue {

    @Id
    private UUID id;

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 4000)
    private String description;

    @Column(name = "address_line_1", nullable = false, length = 250)
    private String addressLine1;

    @Column(name = "address_line_2", length = 250)
    private String addressLine2;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(length = 120)
    private String region;

    @Column(name = "postal_code", length = 32)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VenueStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "venue_amenities", joinColumns = @JoinColumn(name = "venue_id"))
    @Column(name = "amenity", nullable = false, length = 100)
    private Set<String> amenities = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "venue_metadata", joinColumns = @JoinColumn(name = "venue_id"))
    @MapKeyColumn(name = "metadata_key", length = 100)
    @Column(name = "metadata_value", nullable = false, length = 500)
    private Map<String, String> metadata = new HashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    private long version;

    protected Venue() {
    }

    public Venue(
            UUID id,
            UUID organizerId,
            String name,
            String description,
            VenueLocation location,
            String timezone,
            int totalCapacity,
            Set<String> amenities,
            Map<String, String> metadata,
            Instant now) {
        this.id = id;
        this.organizerId = organizerId;
        apply(name, description, location, timezone, totalCapacity, amenities, metadata, now);
        this.status = VenueStatus.ACTIVE;
        this.createdAt = now;
    }

    public void update(
            String name,
            String description,
            VenueLocation location,
            String timezone,
            int totalCapacity,
            Set<String> amenities,
            Map<String, String> metadata,
            Instant now) {
        apply(name, description, location, timezone, totalCapacity, amenities, metadata, now);
    }

    private void apply(
            String name,
            String description,
            VenueLocation location,
            String timezone,
            int totalCapacity,
            Set<String> amenities,
            Map<String, String> metadata,
            Instant now) {
        this.name = name;
        this.description = description;
        this.addressLine1 = location.addressLine1();
        this.addressLine2 = location.addressLine2();
        this.city = location.city();
        this.region = location.region();
        this.postalCode = location.postalCode();
        this.countryCode = location.countryCode();
        this.latitude = location.latitude();
        this.longitude = location.longitude();
        this.timezone = timezone;
        this.totalCapacity = totalCapacity;
        this.amenities.clear();
        this.amenities.addAll(amenities);
        this.metadata.clear();
        this.metadata.putAll(metadata);
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        status = VenueStatus.ARCHIVED;
        archivedAt = now;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public VenueLocation getLocation() {
        return new VenueLocation(
                addressLine1, addressLine2, city, region, postalCode, countryCode, latitude, longitude);
    }

    public String getTimezone() {
        return timezone;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public VenueStatus getStatus() {
        return status;
    }

    public Set<String> getAmenities() {
        return Set.copyOf(amenities);
    }

    public Map<String, String> getMetadata() {
        return Map.copyOf(metadata);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
