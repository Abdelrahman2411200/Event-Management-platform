package com.eventplatform.attendee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "registrations")
public class Registration {

    @Id
    private UUID id;
    @Column(name = "attendee_id", nullable = false)
    private UUID attendeeId;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RegistrationStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Registration() {
    }

    public Registration(UUID id, UUID attendeeId, UUID eventId, RegistrationStatus status, Instant now) {
        this.id = id;
        this.attendeeId = attendeeId;
        this.eventId = eventId;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void confirm(Instant now) { transition(RegistrationStatus.CONFIRMED, now); }
    public void expire(Instant now) { transition(RegistrationStatus.EXPIRED, now); }
    public void cancel(Instant now) { transition(RegistrationStatus.CANCELLED, now); }
    public void refund(Instant now) { transition(RegistrationStatus.REFUNDED, now); }

    private void transition(RegistrationStatus target, Instant now) {
        status = target;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getAttendeeId() { return attendeeId; }
    public UUID getEventId() { return eventId; }
    public RegistrationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
