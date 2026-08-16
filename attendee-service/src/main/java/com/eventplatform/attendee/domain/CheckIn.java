package com.eventplatform.attendee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "check_ins")
public class CheckIn {

    @Id
    private UUID id;
    @Column(name = "ticket_id", nullable = false, unique = true)
    private UUID ticketId;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "scanner_id", nullable = false)
    private UUID scannerId;
    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    protected CheckIn() {
    }

    public CheckIn(UUID id, UUID ticketId, UUID eventId, UUID scannerId, Instant checkedInAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.eventId = eventId;
        this.scannerId = scannerId;
        this.checkedInAt = checkedInAt;
    }

    public UUID getId() { return id; }
    public UUID getTicketId() { return ticketId; }
    public UUID getEventId() { return eventId; }
    public UUID getScannerId() { return scannerId; }
    public Instant getCheckedInAt() { return checkedInAt; }
}
