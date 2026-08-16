package com.eventplatform.notification.domain;

import com.eventplatform.kafka.OutboxRetryPolicy;
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
@Table(name = "notification_intents")
public class NotificationIntent {
    @Id private UUID id;
    @Column(name = "notification_key", nullable = false, unique = true, length = 400) private String notificationKey;
    @Column(name = "source_message_id", nullable = false) private UUID sourceMessageId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "business_id", nullable = false) private UUID businessId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 48) private NotificationType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private NotificationChannel channel;
    @Column(nullable = false, length = 320) private String destination;
    @Column(name = "template_code", nullable = false, length = 80) private String templateCode;
    @Column(nullable = false, length = 16) private String locale;
    @Column(name = "variables_json", nullable = false, columnDefinition = "TEXT") private String variablesJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private NotificationStatus status;
    @Column(name = "scheduled_at", nullable = false) private Instant scheduledAt;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "max_attempts", nullable = false) private int maxAttempts;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "dead_lettered_at") private Instant deadLetteredAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected NotificationIntent() {
    }

    public NotificationIntent(
            UUID id, String notificationKey, UUID sourceMessageId, UUID userId, UUID businessId,
            NotificationType type, NotificationChannel channel, String destination,
            String templateCode, String locale, String variablesJson, Instant scheduledAt,
            int maxAttempts, Instant now) {
        this.id = id;
        this.notificationKey = notificationKey;
        this.sourceMessageId = sourceMessageId;
        this.userId = userId;
        this.businessId = businessId;
        this.type = type;
        this.channel = channel;
        this.destination = destination;
        this.templateCode = templateCode;
        this.locale = locale;
        this.variablesJson = variablesJson;
        this.status = NotificationStatus.PENDING;
        this.scheduledAt = scheduledAt;
        this.nextAttemptAt = scheduledAt;
        this.maxAttempts = maxAttempts;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public int beginAttempt(Instant now) {
        attemptCount++;
        status = NotificationStatus.FAILED;
        updatedAt = now;
        return attemptCount;
    }

    public void sent(Instant now) {
        status = NotificationStatus.SENT;
        sentAt = now;
        lastError = null;
        updatedAt = now;
    }

    public void failed(String error, Instant now) {
        lastError = OutboxRetryPolicy.safeError(error);
        if (attemptCount >= maxAttempts) {
            status = NotificationStatus.DEAD_LETTERED;
            deadLetteredAt = now;
        } else {
            status = NotificationStatus.RETRY_SCHEDULED;
            nextAttemptAt = OutboxRetryPolicy.nextAttempt(now, attemptCount);
        }
        updatedAt = now;
    }

    public void reschedule(Instant when, String variablesJson, Instant now) {
        if (status == NotificationStatus.PENDING
                || status == NotificationStatus.RETRY_SCHEDULED
                || status == NotificationStatus.CANCELLED) {
            scheduledAt = when;
            nextAttemptAt = when;
            this.variablesJson = variablesJson;
            status = NotificationStatus.PENDING;
            updatedAt = now;
        }
    }

    public void cancel(Instant now) {
        if (status == NotificationStatus.PENDING || status == NotificationStatus.RETRY_SCHEDULED) {
            status = NotificationStatus.CANCELLED;
            updatedAt = now;
        }
    }

    public UUID getId() { return id; }
    public String getNotificationKey() { return notificationKey; }
    public UUID getSourceMessageId() { return sourceMessageId; }
    public UUID getUserId() { return userId; }
    public UUID getBusinessId() { return businessId; }
    public NotificationType getType() { return type; }
    public NotificationChannel getChannel() { return channel; }
    public String getDestination() { return destination; }
    public String getTemplateCode() { return templateCode; }
    public String getLocale() { return locale; }
    public String getVariablesJson() { return variablesJson; }
    public NotificationStatus getStatus() { return status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastError() { return lastError; }
    public Instant getSentAt() { return sentAt; }
}
