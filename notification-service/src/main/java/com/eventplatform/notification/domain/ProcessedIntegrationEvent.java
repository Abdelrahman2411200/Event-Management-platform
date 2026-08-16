package com.eventplatform.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_integration_events")
public class ProcessedIntegrationEvent {
    @Id @Column(name = "message_id") private UUID messageId;
    @Column(name = "event_type", nullable = false, length = 160) private String eventType;
    @Column(name = "processed_at", nullable = false) private Instant processedAt;

    protected ProcessedIntegrationEvent() {
    }

    public ProcessedIntegrationEvent(UUID messageId, String eventType, Instant processedAt) {
        this.messageId = messageId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }
}
