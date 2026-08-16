package com.eventplatform.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="payment_ticket_projections")
public class PaymentTicketProjection {
    @Id @Column(name="ticket_id") private UUID ticketId; @Column(name="booking_id",nullable=false) private UUID bookingId;
    @Column(name="attendee_id",nullable=false) private UUID attendeeId; @Column(name="event_id",nullable=false) private UUID eventId;
    @Column(nullable=false,length=32) private String status; @Column(name="issued_at",nullable=false) private Instant issuedAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected PaymentTicketProjection(){}
    public PaymentTicketProjection(UUID ticketId,UUID bookingId,UUID attendeeId,UUID eventId,Instant issuedAt){this.ticketId=ticketId;this.bookingId=bookingId;this.attendeeId=attendeeId;this.eventId=eventId;status="ISSUED";this.issuedAt=issuedAt;updatedAt=issuedAt;}
    public void checkedIn(Instant now){status="CHECKED_IN";updatedAt=now;} public void refunded(Instant now){status="REFUNDED";updatedAt=now;}
    public UUID getTicketId(){return ticketId;} public UUID getBookingId(){return bookingId;} public UUID getAttendeeId(){return attendeeId;} public String getStatus(){return status;}
}
