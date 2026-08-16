package com.eventplatform.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "notification_recipients")
public class NotificationRecipient {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(length = 320) private String email;
    @Column(name = "phone_number", length = 32) private String phoneNumber;
    @Column(nullable = false, length = 16) private String locale;
    @Column(name = "display_name", length = 160) private String displayName;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected NotificationRecipient() {
    }

    public NotificationRecipient(
            UUID userId, String email, String phoneNumber, String locale, String displayName, Instant now) {
        this.userId = userId;
        update(email, phoneNumber, locale, displayName, now);
    }

    public void update(String email, String phoneNumber, String locale, String displayName, Instant now) {
        if (email != null && !email.isBlank()) this.email = email.trim().toLowerCase(Locale.ROOT);
        if (phoneNumber != null && !phoneNumber.isBlank()) this.phoneNumber = phoneNumber.trim();
        if (locale != null && !locale.isBlank()) this.locale = locale.trim();
        if (this.locale == null) this.locale = "en";
        if (displayName != null && !displayName.isBlank()) this.displayName = displayName.trim();
        this.updatedAt = now;
    }

    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getLocale() { return locale; }
    public String getDisplayName() { return displayName; }
}
