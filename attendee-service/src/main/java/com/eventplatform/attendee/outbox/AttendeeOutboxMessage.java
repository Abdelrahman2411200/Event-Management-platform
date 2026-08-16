package com.eventplatform.attendee.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import com.eventplatform.kafka.OutboxRetryPolicy;

@Entity
@Table(name = "attendee_outbox_messages")
public class AttendeeOutboxMessage {

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

    protected AttendeeOutboxMessage() {
    }

    public AttendeeOutboxMessage(
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

    public void markPublished(Instant now) {
        publishedAt = now;
        publishAttempts++;
        lastError = null;
    }

    public void markFailed(String error, Instant now, int maxAttempts) {
        publishAttempts++;
        lastError = OutboxRetryPolicy.safeError(error);
        if (publishAttempts >= maxAttempts) deadLetteredAt = now;
        else nextAttemptAt = OutboxRetryPolicy.nextAttempt(now, publishAttempts);
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getAggregateType() { return aggregateType; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public String getPayload() { return payload; }
    public String getCorrelationId() { return correlationId; }
    public String getTraceparent() { return traceparent; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getPublishAttempts() { return publishAttempts; }
    public boolean isDeadLettered() { return deadLetteredAt != null; }
}
