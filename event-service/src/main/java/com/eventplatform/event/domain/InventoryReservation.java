package com.eventplatform.event.domain;

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
@Table(name = "inventory_reservations")
public class InventoryReservation {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InventoryReservationStatus status;

    @Column(name = "reserve_idempotency_key", nullable = false, unique = true, length = 128)
    private String reserveIdempotencyKey;

    @Column(name = "release_idempotency_key", unique = true, length = 128)
    private String releaseIdempotencyKey;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected InventoryReservation() {
    }

    public InventoryReservation(
            UUID id,
            UUID eventId,
            UUID ticketTypeId,
            UUID requesterId,
            int quantity,
            String reserveIdempotencyKey,
            Instant expiresAt,
            Instant now) {
        this.id = id;
        this.eventId = eventId;
        this.ticketTypeId = ticketTypeId;
        this.requesterId = requesterId;
        this.quantity = quantity;
        this.status = InventoryReservationStatus.ACTIVE;
        this.reserveIdempotencyKey = reserveIdempotencyKey;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean matches(UUID eventId, UUID ticketTypeId, UUID requesterId, int quantity) {
        return this.eventId.equals(eventId)
                && this.ticketTypeId.equals(ticketTypeId)
                && this.requesterId.equals(requesterId)
                && this.quantity == quantity;
    }

    public void release(String idempotencyKey, Instant now) {
        if (releaseIdempotencyKey == null) {
            releaseIdempotencyKey = idempotencyKey;
        }
        if (status == InventoryReservationStatus.ACTIVE) {
            status = InventoryReservationStatus.RELEASED;
            releasedAt = now;
            updatedAt = now;
        }
    }

    public void expire(Instant now) {
        if (status == InventoryReservationStatus.ACTIVE) {
            status = InventoryReservationStatus.EXPIRED;
            releasedAt = now;
            updatedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public int getQuantity() {
        return quantity;
    }

    public InventoryReservationStatus getStatus() {
        return status;
    }

    public String getReserveIdempotencyKey() {
        return reserveIdempotencyKey;
    }

    public String getReleaseIdempotencyKey() {
        return releaseIdempotencyKey;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
