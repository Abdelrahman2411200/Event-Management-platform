package com.eventplatform.event.integration;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="processed_integration_events") public class ProcessedIntegrationEvent {@Id @Column(name="event_id") private UUID id;@Column(name="event_type",nullable=false,length=160) private String type;@Column(name="processed_at",nullable=false) private Instant processedAt;protected ProcessedIntegrationEvent(){}public ProcessedIntegrationEvent(UUID id,String type,Instant at){this.id=id;this.type=type;processedAt=at;}}
