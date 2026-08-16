package com.eventplatform.attendee.domain;

import jakarta.persistence.*; import java.time.*; import java.util.UUID;

@Entity @Table(name="booking_sagas")
public class BookingSaga {
 @Id @Column(name="booking_id") private UUID bookingId; @Column(name="payment_id") private UUID paymentId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=48) private BookingSagaState state;
 @Column(name="failure_code",length=80) private String failureCode; @Column(name="failure_reason",length=500) private String failureReason;
 @Column(name="recovery_attempts",nullable=false) private int recoveryAttempts; @Column(name="next_action_at") private Instant nextActionAt;
 @Column(name="created_at",nullable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt; @Version private long version;
 protected BookingSaga(){}
 public BookingSaga(UUID bookingId,BookingSagaState state,Instant now){this.bookingId=bookingId;this.state=state;createdAt=now;updatedAt=now;}
 public void paymentProcessing(UUID paymentId,Instant now){this.paymentId=paymentId;state=BookingSagaState.PAYMENT_PROCESSING;updatedAt=now;}
 public void confirmationPending(UUID paymentId,Instant now){this.paymentId=paymentId;state=BookingSagaState.INVENTORY_CONFIRMATION_PENDING;failureCode=null;failureReason=null;recoveryAttempts=0;nextActionAt=now.plusSeconds(5);updatedAt=now;}
 public void confirmed(Instant now){state=BookingSagaState.CONFIRMED;nextActionAt=null;updatedAt=now;}
 public void paymentFailed(UUID paymentId,String code,String reason,Instant now){this.paymentId=paymentId;state=BookingSagaState.PAYMENT_FAILED;failureCode=code;failureReason=reason;nextActionAt=null;updatedAt=now;}
 public void compensate(UUID paymentId,String code,String reason,Instant now){this.paymentId=paymentId;state=BookingSagaState.COMPENSATION_PENDING;failureCode=code;failureReason=reason;nextActionAt=now.plusSeconds(5);updatedAt=now;}
 public void refunded(boolean full,Instant now){state=full?BookingSagaState.REFUNDED:BookingSagaState.PARTIALLY_REFUNDED;nextActionAt=null;updatedAt=now;}
 public void expired(Instant now){state=BookingSagaState.EXPIRED;nextActionAt=null;updatedAt=now;}
 public void retry(Instant now){recoveryAttempts++;long delay=Math.min(300,1L<<Math.min(recoveryAttempts+2,8));nextActionAt=now.plusSeconds(delay);updatedAt=now;}
 public UUID getBookingId(){return bookingId;} public UUID getPaymentId(){return paymentId;} public BookingSagaState getState(){return state;} public String getFailureCode(){return failureCode;} public String getFailureReason(){return failureReason;} public int getRecoveryAttempts(){return recoveryAttempts;} public Instant getNextActionAt(){return nextActionAt;}
}
