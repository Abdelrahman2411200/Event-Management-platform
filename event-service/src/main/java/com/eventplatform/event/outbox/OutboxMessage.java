package com.eventplatform.event.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(length = 256)
    private String traceparent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Version
    private long version;

    protected OutboxMessage() {
    }

    public OutboxMessage(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            String payload,
            String correlationId,
            String traceparent,
            Instant occurredAt) {
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

    public void markFailed(String error, Instant now) {
        publishAttempts++;
        long delaySeconds = Math.min(60, 1L << Math.min(publishAttempts, 5));
        nextAttemptAt = now.plus(delaySeconds, ChronoUnit.SECONDS);
        lastError = error == null ? "Kafka publication failed" : error.substring(0, Math.min(error.length(), 500));
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getTraceparent() {
        return traceparent;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
