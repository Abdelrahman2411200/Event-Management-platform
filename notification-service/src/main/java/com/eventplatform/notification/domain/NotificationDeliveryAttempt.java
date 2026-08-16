package com.eventplatform.notification.domain;

import com.eventplatform.kafka.OutboxRetryPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_delivery_attempts")
public class NotificationDeliveryAttempt {
    @Id private UUID id;
    @Column(name = "intent_id", nullable = false) private UUID intentId;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber;
    @Column(nullable = false, length = 80) private String provider;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private DeliveryAttemptStatus status;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "provider_message_id", length = 160) private String providerMessageId;
    @Column(name = "failure_reason", length = 500) private String failureReason;

    protected NotificationDeliveryAttempt() {
    }

    public NotificationDeliveryAttempt(UUID id, UUID intentId, int attemptNumber, String provider, Instant now) {
        this.id = id;
        this.intentId = intentId;
        this.attemptNumber = attemptNumber;
        this.provider = provider;
        this.status = DeliveryAttemptStatus.STARTED;
        this.startedAt = now;
    }

    public void sent(String providerMessageId, Instant now) {
        status = DeliveryAttemptStatus.SENT;
        this.providerMessageId = providerMessageId;
        completedAt = now;
    }

    public void failed(String reason, Instant now) {
        status = DeliveryAttemptStatus.FAILED;
        failureReason = OutboxRetryPolicy.safeError(reason);
        completedAt = now;
    }

    public UUID getIntentId() { return intentId; }
    public int getAttemptNumber() { return attemptNumber; }
    public DeliveryAttemptStatus getStatus() { return status; }
}
