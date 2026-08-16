package com.eventplatform.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "reminders_enabled", nullable = false) private boolean remindersEnabled;
    @Column(name = "sms_enabled", nullable = false) private boolean smsEnabled;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected NotificationPreference() {
    }

    public NotificationPreference(UUID userId, Instant now) {
        this.userId = userId;
        this.remindersEnabled = true;
        this.updatedAt = now;
    }

    public void update(boolean remindersEnabled, boolean smsEnabled, Instant now) {
        this.remindersEnabled = remindersEnabled;
        this.smsEnabled = smsEnabled;
        this.updatedAt = now;
    }

    public UUID getUserId() { return userId; }
    public boolean isRemindersEnabled() { return remindersEnabled; }
    public boolean isSmsEnabled() { return smsEnabled; }
    public Instant getUpdatedAt() { return updatedAt; }
}
