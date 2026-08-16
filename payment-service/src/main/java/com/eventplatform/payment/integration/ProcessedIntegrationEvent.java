package com.eventplatform.payment.integration;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="processed_integration_events")
public class ProcessedIntegrationEvent {
    @Id @Column(name="event_id") private UUID eventId; @Column(name="event_type",nullable=false,length=160) private String eventType;
    @Column(name="processed_at",nullable=false) private Instant processedAt; protected ProcessedIntegrationEvent(){}
    public ProcessedIntegrationEvent(UUID id,String type,Instant at){eventId=id;eventType=type;processedAt=at;}
}
