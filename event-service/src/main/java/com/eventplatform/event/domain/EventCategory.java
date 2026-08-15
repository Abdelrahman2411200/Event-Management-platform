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
@Table(name = "event_categories")
public class EventCategory {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CategoryStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    private long version;

    protected EventCategory() {
    }

    public EventCategory(UUID id, String slug, String name, String description, Instant now) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.status = CategoryStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String slug, String name, String description, Instant now) {
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        this.status = CategoryStatus.ARCHIVED;
        this.archivedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CategoryStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
