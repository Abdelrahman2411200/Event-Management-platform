package com.eventplatform.attendee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    private UUID id;
    @Column(name = "attendee_id", nullable = false)
    private UUID attendeeId;
    @Column(name = "registration_id", nullable = false, unique = true)
    private UUID registrationId;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookingStatus status;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Booking() {
    }

    public Booking(
            UUID id,
            UUID attendeeId,
            UUID registrationId,
            UUID eventId,
            BigDecimal totalAmount,
            String currency,
            Instant holdExpiresAt,
            Instant now) {
        this.id = id;
        this.attendeeId = attendeeId;
        this.registrationId = registrationId;
        this.eventId = eventId;
        this.status = BookingStatus.HOLD_CREATED;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.holdExpiresAt = holdExpiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void paymentPending(Instant now) { transition(BookingStatus.PAYMENT_PENDING, now); }
    public void paymentProcessing(Instant now) { transition(BookingStatus.PAYMENT_PROCESSING, now); }
    public void confirmationPending(Instant now) { transition(BookingStatus.CONFIRMATION_PENDING, now); }
    public void paymentFailed(Instant now) { transition(BookingStatus.PAYMENT_FAILED, now); }
    public void compensationPending(Instant now) { transition(BookingStatus.COMPENSATION_PENDING, now); }
    public void partiallyRefunded(Instant now) { transition(BookingStatus.PARTIALLY_REFUNDED, now); }
    public void confirm(Instant now) { transition(BookingStatus.CONFIRMED, now); }
    public void expire(Instant now) { transition(BookingStatus.EXPIRED, now); }
    public void cancel(Instant now) { transition(BookingStatus.CANCELLED, now); }
    public void refund(Instant now) { transition(BookingStatus.REFUNDED, now); }

    private void transition(BookingStatus target, Instant now) {
        status = target;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getAttendeeId() { return attendeeId; }
    public UUID getRegistrationId() { return registrationId; }
    public UUID getEventId() { return eventId; }
    public BookingStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
