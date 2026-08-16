package com.eventplatform.attendee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "check_in_attempts")
public class CheckInAttempt {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScanOperation operation;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private ScanOutcome outcome;
    @Column(name = "scanner_id", nullable = false)
    private UUID scannerId;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "ticket_id")
    private UUID ticketId;
    @Column(name = "token_fingerprint", nullable = false, length = 64)
    private String tokenFingerprint;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Column(name = "ticket_checked_in_at")
    private Instant ticketCheckedInAt;
    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected CheckInAttempt() {
    }

    public CheckInAttempt(
            UUID id, ScanOperation operation, ScanOutcome outcome, UUID scannerId, UUID eventId,
            UUID ticketId, String tokenFingerprint, String idempotencyKey,
            Instant ticketCheckedInAt, Instant attemptedAt) {
        this.id = id;
        this.operation = operation;
        this.outcome = outcome;
        this.scannerId = scannerId;
        this.eventId = eventId;
        this.ticketId = ticketId;
        this.tokenFingerprint = tokenFingerprint;
        this.idempotencyKey = idempotencyKey;
        this.ticketCheckedInAt = ticketCheckedInAt;
        this.attemptedAt = attemptedAt;
    }

    public ScanOperation getOperation() { return operation; }
    public ScanOutcome getOutcome() { return outcome; }
    public UUID getScannerId() { return scannerId; }
    public UUID getEventId() { return eventId; }
    public UUID getTicketId() { return ticketId; }
    public String getTokenFingerprint() { return tokenFingerprint; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getTicketCheckedInAt() { return ticketCheckedInAt; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
