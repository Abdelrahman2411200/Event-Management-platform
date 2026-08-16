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
@Table(name = "ticket_holds")
public class TicketHold {

    @Id
    private UUID id;
    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;
    @Column(name = "inventory_reservation_id", nullable = false, unique = true)
    private UUID inventoryReservationId;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;
    @Column(nullable = false)
    private int quantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TicketHoldStatus status;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "released_at")
    private Instant releasedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected TicketHold() {
    }

    public TicketHold(
            UUID id, UUID bookingId, UUID inventoryReservationId, UUID eventId,
            UUID ticketTypeId, int quantity, TicketHoldStatus status, Instant expiresAt,
            Instant confirmedAt, Instant now) {
        this.id = id;
        this.bookingId = bookingId;
        this.inventoryReservationId = inventoryReservationId;
        this.eventId = eventId;
        this.ticketTypeId = ticketTypeId;
        this.quantity = quantity;
        this.status = status;
        this.expiresAt = expiresAt;
        this.confirmedAt = confirmedAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void expire(Instant now) {
        if (status == TicketHoldStatus.ACTIVE) {
            status = TicketHoldStatus.EXPIRED;
            releasedAt = now;
            updatedAt = now;
        }
    }

    public void release(Instant now) {
        if (status == TicketHoldStatus.ACTIVE) {
            status = TicketHoldStatus.RELEASED;
            releasedAt = now;
            updatedAt = now;
        }
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public UUID getInventoryReservationId() { return inventoryReservationId; }
    public UUID getEventId() { return eventId; }
    public UUID getTicketTypeId() { return ticketTypeId; }
    public int getQuantity() { return quantity; }
    public TicketHoldStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
