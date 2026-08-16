package com.eventplatform.attendee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_line_items")
public class BookingLineItem {

    @Id
    private UUID id;
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;
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
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BookingLineItem() {
    }

    public BookingLineItem(
            UUID id, UUID bookingId, UUID eventId, UUID eventOrganizerId, UUID ticketTypeId,
            String eventTitle, String ticketTypeName, Instant eventStartsAt, Instant eventEndsAt,
            UUID venueId, UUID venueSpaceId, BigDecimal unitPrice, String currency, int quantity, Instant now) {
        this.id = id;
        this.bookingId = bookingId;
        this.eventId = eventId;
        this.eventOrganizerId = eventOrganizerId;
        this.ticketTypeId = ticketTypeId;
        this.eventTitle = eventTitle;
        this.ticketTypeName = ticketTypeName;
        this.eventStartsAt = eventStartsAt;
        this.eventEndsAt = eventEndsAt;
        this.venueId = venueId;
        this.venueSpaceId = venueSpaceId;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public UUID getEventId() { return eventId; }
    public UUID getEventOrganizerId() { return eventOrganizerId; }
    public UUID getTicketTypeId() { return ticketTypeId; }
    public String getEventTitle() { return eventTitle; }
    public String getTicketTypeName() { return ticketTypeName; }
    public Instant getEventStartsAt() { return eventStartsAt; }
    public Instant getEventEndsAt() { return eventEndsAt; }
    public UUID getVenueId() { return venueId; }
    public UUID getVenueSpaceId() { return venueSpaceId; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getCurrency() { return currency; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
