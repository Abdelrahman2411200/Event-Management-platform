package com.eventplatform.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="booking_payment_orders")
public class BookingPaymentOrder {
    @Id @Column(name="booking_id") private UUID bookingId;
    @Column(name="attendee_id",nullable=false) private UUID attendeeId;
    @Column(name="event_id",nullable=false) private UUID eventId;
    @Column(name="event_organizer_id",nullable=false) private UUID eventOrganizerId;
    @Column(name="inventory_reservation_id",nullable=false) private UUID inventoryReservationId;
    @Column(name="ticket_type_id",nullable=false) private UUID ticketTypeId;
    @Column(nullable=false) private int quantity;
    @Column(name="unit_price",nullable=false,precision=19,scale=2) private BigDecimal unitPrice;
    @Column(name="total_amount",nullable=false,precision=19,scale=2) private BigDecimal totalAmount;
    @Column(nullable=false,length=3) private String currency;
    @Column(name="event_starts_at",nullable=false) private Instant eventStartsAt;
    @Column(name="event_title",length=240) private String eventTitle;
    @Column(name="attendee_email",length=320) private String attendeeEmail;
    @Column(name="attendee_phone",length=32) private String attendeePhone;
    @Column(name="attendee_locale",length=16) private String attendeeLocale;
    @Column(name="hold_expires_at",nullable=false) private Instant holdExpiresAt;
    @Column(nullable=false,length=32) private String status;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;
    protected BookingPaymentOrder() {}
    public BookingPaymentOrder(UUID bookingId, UUID attendeeId, UUID eventId, UUID organizerId, UUID reservationId,
            UUID ticketTypeId, int quantity, BigDecimal unitPrice, BigDecimal totalAmount, String currency,
            Instant eventStartsAt, Instant holdExpiresAt, Instant now) {
        this(bookingId, attendeeId, eventId, organizerId, reservationId, ticketTypeId, quantity,
                unitPrice, totalAmount, currency, eventStartsAt, holdExpiresAt,
                null, null, null, "en", now);
    }
    public BookingPaymentOrder(UUID bookingId, UUID attendeeId, UUID eventId, UUID organizerId, UUID reservationId,
            UUID ticketTypeId, int quantity, BigDecimal unitPrice, BigDecimal totalAmount, String currency,
            Instant eventStartsAt, Instant holdExpiresAt, String eventTitle, String attendeeEmail,
            String attendeePhone, String attendeeLocale, Instant now) {
        this.bookingId=bookingId; this.attendeeId=attendeeId; this.eventId=eventId; this.eventOrganizerId=organizerId;
        this.inventoryReservationId=reservationId; this.ticketTypeId=ticketTypeId; this.quantity=quantity;
        this.unitPrice=unitPrice; this.totalAmount=totalAmount; this.currency=currency; this.eventStartsAt=eventStartsAt;
        this.holdExpiresAt=holdExpiresAt; this.eventTitle=eventTitle; this.attendeeEmail=attendeeEmail;
        this.attendeePhone=attendeePhone; this.attendeeLocale=attendeeLocale == null ? "en" : attendeeLocale;
        this.status="PAYMENT_PENDING"; this.createdAt=now; this.updatedAt=now;
    }
    public UUID getBookingId(){return bookingId;} public UUID getAttendeeId(){return attendeeId;}
    public UUID getEventId(){return eventId;} public UUID getEventOrganizerId(){return eventOrganizerId;}
    public UUID getInventoryReservationId(){return inventoryReservationId;} public UUID getTicketTypeId(){return ticketTypeId;}
    public int getQuantity(){return quantity;} public BigDecimal getUnitPrice(){return unitPrice;}
    public BigDecimal getTotalAmount(){return totalAmount;} public String getCurrency(){return currency;}
    public Instant getEventStartsAt(){return eventStartsAt;} public Instant getHoldExpiresAt(){return holdExpiresAt;}
    public String getEventTitle(){return eventTitle;} public String getAttendeeEmail(){return attendeeEmail;}
    public String getAttendeePhone(){return attendeePhone;} public String getAttendeeLocale(){return attendeeLocale;}
}
