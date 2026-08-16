package com.eventplatform.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "local_deliveries")
public class LocalDelivery {
    @Id private UUID id;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 400) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private NotificationChannel channel;
    @Column(nullable = false, length = 320) private String destination;
    @Column(length = 500) private String subject;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected LocalDelivery() {
    }

    public LocalDelivery(
            UUID id, String idempotencyKey, NotificationChannel channel,
            String destination, String subject, String body, Instant createdAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.channel = channel;
        this.destination = destination;
        this.subject = subject;
        this.body = body;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public NotificationChannel getChannel() { return channel; }
    public String getDestination() { return destination; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
