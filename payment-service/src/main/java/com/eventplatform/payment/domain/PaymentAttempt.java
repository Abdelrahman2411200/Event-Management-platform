package com.eventplatform.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="payment_attempts")
public class PaymentAttempt {
    @Id private UUID id; @Column(name="payment_id",nullable=false) private UUID paymentId;
    @Column(name="attendee_id",nullable=false) private UUID attendeeId;
    @Column(name="idempotency_key",nullable=false,length=128) private String idempotencyKey;
    @Column(name="payment_method_fingerprint",nullable=false,length=64) private String paymentMethodFingerprint;
    @Column(name="provider_attempt_id",length=160) private String providerAttemptId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private PaymentAttemptStatus status;
    @Column(name="failure_code",length=80) private String failureCode; @Column(name="failure_reason",length=500) private String failureReason;
    @Column(name="created_at",nullable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="completed_at") private Instant completedAt; @Version private long version;
    protected PaymentAttempt(){}
    public PaymentAttempt(UUID id,UUID paymentId,UUID attendeeId,String key,String fingerprint,Instant now){this.id=id;this.paymentId=paymentId;this.attendeeId=attendeeId;idempotencyKey=key;paymentMethodFingerprint=fingerprint;status=PaymentAttemptStatus.PROCESSING;createdAt=now;updatedAt=now;}
    public void processing(String providerId,Instant now){providerAttemptId=providerId;updatedAt=now;}
    public void succeed(String providerId,Instant now){providerAttemptId=providerId;status=PaymentAttemptStatus.SUCCEEDED;completedAt=now;updatedAt=now;}
    public void fail(String providerId,String code,String reason,Instant now){providerAttemptId=providerId;status=PaymentAttemptStatus.FAILED;failureCode=code;failureReason=reason;completedAt=now;updatedAt=now;}
    public UUID getId(){return id;} public UUID getPaymentId(){return paymentId;} public UUID getAttendeeId(){return attendeeId;} public String getIdempotencyKey(){return idempotencyKey;}
    public String getPaymentMethodFingerprint(){return paymentMethodFingerprint;} public String getProviderAttemptId(){return providerAttemptId;} public PaymentAttemptStatus getStatus(){return status;}
    public String getFailureCode(){return failureCode;} public String getFailureReason(){return failureReason;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
