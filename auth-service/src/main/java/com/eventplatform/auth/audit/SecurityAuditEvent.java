package com.eventplatform.auth.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_audit_events")
public class SecurityAuditEvent {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(name = "subject_identifier", length = 320)
    private String subjectIdentifier;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(length = 1000)
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected SecurityAuditEvent() {
    }

    public SecurityAuditEvent(
            UUID userId,
            String eventType,
            String outcome,
            String subjectIdentifier,
            UUID sessionId,
            String correlationId,
            String ipAddress,
            String userAgent,
            String details,
            Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.eventType = eventType;
        this.outcome = outcome;
        this.subjectIdentifier = subjectIdentifier;
        this.sessionId = sessionId;
        this.correlationId = correlationId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getSubjectIdentifier() {
        return subjectIdentifier;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
