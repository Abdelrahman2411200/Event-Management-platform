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

    @Column(length = 320)
    private String email;

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

    public AttendeeProfile(UUID id, String email, String displayName, String phoneNumber, String locale, Instant now) {
        this.id = id;
        this.createdAt = now;
        synchronizeIdentity(email, now);
        update(displayName, phoneNumber, locale, now);
    }

    public void synchronizeIdentity(String email, Instant now) {
        if (email != null && !email.isBlank()) {
            this.email = email.trim().toLowerCase(java.util.Locale.ROOT);
            this.updatedAt = now;
        }
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
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getLocale() { return locale; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
