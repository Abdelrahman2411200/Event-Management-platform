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
@Table(name = "tickets")
public class Ticket {

    @Id
    private UUID id;
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;
    @Column(name = "registration_id", nullable = false)
    private UUID registrationId;
    @Column(name = "line_item_id", nullable = false)
    private UUID lineItemId;
    @Column(name = "attendee_id", nullable = false)
    private UUID attendeeId;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "event_organizer_id", nullable = false)
    private UUID eventOrganizerId;
    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;
    @Column(name = "event_title", nullable = false, length = 240)
    private String eventTitle;
    @Column(name = "ticket_type_name", nullable = false, length = 160)
    private String ticketTypeName;
    @Column(name = "event_starts_at", nullable = false)
    private Instant eventStartsAt;
    @Column(name = "event_ends_at", nullable = false)
    private Instant eventEndsAt;
    @Column(name = "venue_id", nullable = false)
    private UUID venueId;
    @Column(name = "venue_space_id")
    private UUID venueSpaceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TicketStatus status;
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;
    @Column(name = "checked_in_at")
    private Instant checkedInAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "refunded_at")
    private Instant refundedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Ticket() {
    }

    public Ticket(UUID id, UUID bookingId, UUID registrationId, BookingLineItem lineItem, UUID attendeeId, Instant now) {
        this.id = id;
        this.bookingId = bookingId;
        this.registrationId = registrationId;
        this.lineItemId = lineItem.getId();
        this.attendeeId = attendeeId;
        this.eventId = lineItem.getEventId();
        this.eventOrganizerId = lineItem.getEventOrganizerId();
        this.ticketTypeId = lineItem.getTicketTypeId();
        this.eventTitle = lineItem.getEventTitle();
        this.ticketTypeName = lineItem.getTicketTypeName();
        this.eventStartsAt = lineItem.getEventStartsAt();
        this.eventEndsAt = lineItem.getEventEndsAt();
        this.venueId = lineItem.getVenueId();
        this.venueSpaceId = lineItem.getVenueSpaceId();
        this.status = TicketStatus.ISSUED;
        this.tokenVersion = 1;
        this.issuedAt = now;
        this.updatedAt = now;
    }

    public void checkIn(Instant now) {
        if (status == TicketStatus.ISSUED) {
            status = TicketStatus.CHECKED_IN;
            checkedInAt = now;
            updatedAt = now;
        }
    }

    public void cancel(Instant now) {
        status = TicketStatus.CANCELLED;
        tokenVersion++;
        cancelledAt = now;
        updatedAt = now;
    }

    public void refund(Instant now) {
        status = TicketStatus.REFUNDED;
        tokenVersion++;
        refundedAt = now;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public UUID getRegistrationId() { return registrationId; }
    public UUID getLineItemId() { return lineItemId; }
    public UUID getAttendeeId() { return attendeeId; }
    public UUID getEventId() { return eventId; }
    public UUID getEventOrganizerId() { return eventOrganizerId; }
    public UUID getTicketTypeId() { return ticketTypeId; }
    public String getEventTitle() { return eventTitle; }
    public String getTicketTypeName() { return ticketTypeName; }
    public Instant getEventStartsAt() { return eventStartsAt; }
    public Instant getEventEndsAt() { return eventEndsAt; }
    public UUID getVenueId() { return venueId; }
    public UUID getVenueSpaceId() { return venueSpaceId; }
    public TicketStatus getStatus() { return status; }
    public int getTokenVersion() { return tokenVersion; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
