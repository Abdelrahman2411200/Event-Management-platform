package com.eventplatform.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="provider_webhook_events")
public class ProviderWebhookEvent {
    @Id private UUID id; @Column(nullable=false,length=40) private String provider;
    @Column(name="provider_event_id",nullable=false,length=160) private String providerEventId;
    @Column(name="payload_fingerprint",nullable=false,length=64) private String payloadFingerprint;
    @Column(name="event_type",nullable=false,length=80) private String eventType; @Column(nullable=false,length=32) private String status;
    @Column(name="received_at",nullable=false) private Instant receivedAt; @Column(name="processed_at") private Instant processedAt;
    @Column(name="failure_reason",length=500) private String failureReason;
    protected ProviderWebhookEvent(){}
    public ProviderWebhookEvent(UUID id,String provider,String eventId,String fingerprint,String type,Instant now){this.id=id;this.provider=provider;providerEventId=eventId;payloadFingerprint=fingerprint;eventType=type;status="RECEIVED";receivedAt=now;}
    public void processed(Instant now){status="PROCESSED";processedAt=now;} public void ignored(Instant now){status="IGNORED";processedAt=now;}
}
