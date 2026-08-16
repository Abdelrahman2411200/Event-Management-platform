package com.eventplatform.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="payments")
public class Payment {
    @Id private UUID id;
    @Column(name="booking_id",nullable=false,unique=true) private UUID bookingId;
    @Column(name="attendee_id",nullable=false) private UUID attendeeId;
    @Column(name="event_id",nullable=false) private UUID eventId;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
    @Column(name="refunded_amount",nullable=false,precision=19,scale=2) private BigDecimal refundedAmount;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,length=40) private String provider;
    @Column(name="provider_payment_id",length=160) private String providerPaymentId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private PaymentStatus status;
    @Column(name="failure_code",length=80) private String failureCode;
    @Column(name="failure_reason",length=500) private String failureReason;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="paid_at") private Instant paidAt;
    @Version private long version;
    protected Payment() {}
    public Payment(UUID id, BookingPaymentOrder order, String provider, Instant now) {
        this.id=id; bookingId=order.getBookingId(); attendeeId=order.getAttendeeId(); eventId=order.getEventId();
        amount=order.getTotalAmount(); refundedAmount=BigDecimal.ZERO; currency=order.getCurrency(); this.provider=provider;
        status=PaymentStatus.CREATED; createdAt=now; updatedAt=now;
    }
    public void processing(String providerId, Instant now){ if(status==PaymentStatus.CREATED||status==PaymentStatus.PROCESSING){status=PaymentStatus.PROCESSING;providerPaymentId=providerId;updatedAt=now;} }
    public boolean succeed(String providerId, Instant now){ if(status==PaymentStatus.SUCCEEDED||status==PaymentStatus.PARTIALLY_REFUNDED||status==PaymentStatus.REFUNDED)return false; if(status==PaymentStatus.FAILED)return false; status=PaymentStatus.SUCCEEDED;providerPaymentId=providerId;failureCode=null;failureReason=null;paidAt=now;updatedAt=now;return true; }
    public boolean fail(String code,String reason,Instant now){ if(status==PaymentStatus.SUCCEEDED||status==PaymentStatus.PARTIALLY_REFUNDED||status==PaymentStatus.REFUNDED||status==PaymentStatus.FAILED)return false;status=PaymentStatus.FAILED;failureCode=code;failureReason=reason;updatedAt=now;return true; }
    public void refunded(BigDecimal value,Instant now){refundedAmount=refundedAmount.add(value);status=refundedAmount.compareTo(amount)>=0?PaymentStatus.REFUNDED:PaymentStatus.PARTIALLY_REFUNDED;updatedAt=now;}
    public UUID getId(){return id;} public UUID getBookingId(){return bookingId;} public UUID getAttendeeId(){return attendeeId;}
    public UUID getEventId(){return eventId;} public BigDecimal getAmount(){return amount;} public BigDecimal getRefundedAmount(){return refundedAmount;}
    public BigDecimal refundableAmount(){return amount.subtract(refundedAmount);} public String getCurrency(){return currency;}
    public String getProvider(){return provider;} public String getProviderPaymentId(){return providerPaymentId;} public PaymentStatus getStatus(){return status;}
    public String getFailureCode(){return failureCode;} public String getFailureReason(){return failureReason;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public Instant getPaidAt(){return paidAt;}
}
