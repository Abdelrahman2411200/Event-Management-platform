package com.eventplatform.auth.audit;

import com.eventplatform.auth.api.AuditEventResponse;
import com.eventplatform.auth.api.RequestMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAuditService.class);
    private final SecurityAuditEventRepository repository;
    private final Clock clock;

    public SecurityAuditService(SecurityAuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(
            SecurityEventType type,
            UUID userId,
            String subjectIdentifier,
            UUID sessionId,
            RequestMetadata metadata,
            String details) {
        record(type, "SUCCESS", userId, subjectIdentifier, sessionId, metadata, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(
            SecurityEventType type,
            UUID userId,
            String subjectIdentifier,
            UUID sessionId,
            RequestMetadata metadata,
            String details) {
        record(type, "FAILURE", userId, subjectIdentifier, sessionId, metadata, details);
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditEventResponse> recent(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return repository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, boundedLimit)).stream()
                .map(AuditEventResponse::from)
                .toList();
    }

    private void record(
            SecurityEventType type,
            String outcome,
            UUID userId,
            String subjectIdentifier,
            UUID sessionId,
            RequestMetadata metadata,
            String details) {
        Instant now = clock.instant();
        repository.save(new SecurityAuditEvent(
                userId,
                type.name(),
                outcome,
                limit(subjectIdentifier, 320),
                sessionId,
                metadata == null ? null : metadata.correlationId(),
                metadata == null ? null : metadata.ipAddress(),
                metadata == null ? null : metadata.userAgent(),
                limit(details, 1000),
                now));
        if ("FAILURE".equals(outcome)) {
            LOGGER.warn("Security event type={} outcome={} userId={} correlationId={}",
                    type, outcome, userId, metadata == null ? null : metadata.correlationId());
        } else {
            LOGGER.info("Security event type={} outcome={} userId={} correlationId={}",
                    type, outcome, userId, metadata == null ? null : metadata.correlationId());
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
