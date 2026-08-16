package com.eventplatform.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_notification_projections")
public class BookingNotificationProjection {
    @Id @Column(name = "booking_id") private UUID bookingId;
    @Column(name = "attendee_id", nullable = false) private UUID attendeeId;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "event_title", nullable = false, length = 240) private String eventTitle;
    @Column(name = "event_starts_at", nullable = false) private Instant eventStartsAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected BookingNotificationProjection() {
    }

    public BookingNotificationProjection(
            UUID bookingId, UUID attendeeId, UUID eventId, String eventTitle, Instant eventStartsAt, Instant now) {
        this.bookingId = bookingId;
        this.attendeeId = attendeeId;
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.eventStartsAt = eventStartsAt;
        this.updatedAt = now;
    }

    public boolean reschedule(Instant startsAt, Instant now) {
        if (eventStartsAt.equals(startsAt)) return false;
        eventStartsAt = startsAt;
        updatedAt = now;
        return true;
    }

    public UUID getBookingId() { return bookingId; }
    public UUID getAttendeeId() { return attendeeId; }
    public UUID getEventId() { return eventId; }
    public String getEventTitle() { return eventTitle; }
    public Instant getEventStartsAt() { return eventStartsAt; }
}
