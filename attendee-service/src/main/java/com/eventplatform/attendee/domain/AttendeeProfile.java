package com.eventplatform.attendee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendee_profiles")
public class AttendeeProfile {

    @Id
    private UUID id;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(nullable = false, length = 16)
    private String locale;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AttendeeProfile() {
    }

    public AttendeeProfile(UUID id, String displayName, String phoneNumber, String locale, Instant now) {
        this.id = id;
        this.createdAt = now;
        update(displayName, phoneNumber, locale, now);
    }

    public void update(String displayName, String phoneNumber, String locale, Instant now) {
        this.displayName = normalize(displayName);
        this.phoneNumber = normalize(phoneNumber);
        this.locale = locale == null || locale.isBlank() ? "en" : locale.trim();
        this.updatedAt = now;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getLocale() { return locale; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
