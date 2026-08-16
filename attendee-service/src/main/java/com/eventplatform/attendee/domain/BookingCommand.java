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
@Table(name = "booking_commands")
public class BookingCommand {

    @Id
    private UUID id;
    @Column(name = "attendee_id", nullable = false)
    private UUID attendeeId;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookingCommandStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected BookingCommand() {
    }

    public BookingCommand(
            UUID id,
            UUID attendeeId,
            String idempotencyKey,
            UUID eventId,
            UUID ticketTypeId,
            int quantity,
            UUID bookingId,
            Instant now) {
        this.id = id;
        this.attendeeId = attendeeId;
        this.idempotencyKey = idempotencyKey;
        this.eventId = eventId;
        this.ticketTypeId = ticketTypeId;
        this.quantity = quantity;
        this.bookingId = bookingId;
        this.status = BookingCommandStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean matches(UUID eventId, UUID ticketTypeId, int quantity) {
        return this.eventId.equals(eventId) && this.ticketTypeId.equals(ticketTypeId) && this.quantity == quantity;
    }

    public void complete(Instant now) {
        status = BookingCommandStatus.COMPLETED;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getAttendeeId() { return attendeeId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getEventId() { return eventId; }
    public UUID getTicketTypeId() { return ticketTypeId; }
    public int getQuantity() { return quantity; }
    public UUID getBookingId() { return bookingId; }
    public BookingCommandStatus getStatus() { return status; }
}
