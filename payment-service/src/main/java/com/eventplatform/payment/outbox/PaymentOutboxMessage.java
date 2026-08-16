package com.eventplatform.payment.outbox;

import com.eventplatform.kafka.OutboxRetryPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_outbox_messages")
public class PaymentOutboxMessage {
    @Id private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 80) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 160) private String eventType;
    @Column(name = "event_version", nullable = false) private int eventVersion;
    @Column(nullable = false, columnDefinition = "TEXT") private String payload;
    @Column(name = "correlation_id", nullable = false, length = 128) private String correlationId;
    @Column(length = 256) private String traceparent;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "publish_attempts", nullable = false) private int publishAttempts;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "dead_lettered_at") private Instant deadLetteredAt;
    @Version private long version;

    protected PaymentOutboxMessage() {
    }

    PaymentOutboxMessage(
            UUID id, String aggregateType, UUID aggregateId, String eventType, int eventVersion,
            String payload, String correlationId, String traceparent, Instant occurredAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.correlationId = correlationId;
        this.traceparent = traceparent;
        this.occurredAt = occurredAt;
        this.nextAttemptAt = occurredAt;
    }

    void markPublished(Instant now) {
        publishedAt = now;
        publishAttempts++;
        lastError = null;
    }

    void markFailed(String error, Instant now, int maxAttempts) {
        publishAttempts++;
        lastError = OutboxRetryPolicy.safeError(error);
        if (publishAttempts >= maxAttempts) deadLetteredAt = now;
        else nextAttemptAt = OutboxRetryPolicy.nextAttempt(now, publishAttempts);
    }

    UUID getId() { return id; }
    String getAggregateType() { return aggregateType; }
    UUID getAggregateId() { return aggregateId; }
    String getEventType() { return eventType; }
    int getEventVersion() { return eventVersion; }
    String getPayload() { return payload; }
    String getCorrelationId() { return correlationId; }
    String getTraceparent() { return traceparent; }
    Instant getOccurredAt() { return occurredAt; }
    int getPublishAttempts() { return publishAttempts; }
    boolean isDeadLettered() { return deadLetteredAt != null; }
}
