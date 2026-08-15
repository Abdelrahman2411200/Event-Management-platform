package com.eventplatform.auth.api;

import com.eventplatform.auth.audit.SecurityAuditEvent;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID userId,
        String eventType,
        String outcome,
        String subjectIdentifier,
        UUID sessionId,
        String correlationId,
        String ipAddress,
        String details,
        Instant occurredAt) {

    public static AuditEventResponse from(SecurityAuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getUserId(),
                event.getEventType(),
                event.getOutcome(),
                event.getSubjectIdentifier(),
                event.getSessionId(),
                event.getCorrelationId(),
                event.getIpAddress(),
                event.getDetails(),
                event.getOccurredAt());
    }
}
