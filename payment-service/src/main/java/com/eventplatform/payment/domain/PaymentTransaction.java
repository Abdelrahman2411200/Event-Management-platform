package com.eventplatform.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="payment_transactions")
public class PaymentTransaction {
    @Id private UUID id; @Column(name="payment_id",nullable=false) private UUID paymentId;
    @Column(name="attempt_id") private UUID attemptId; @Column(name="refund_id") private UUID refundId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private TransactionType type;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private TransactionStatus status;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount; @Column(nullable=false,length=3) private String currency;
    @Column(name="provider_transaction_id",length=160) private String providerTransactionId;
    @Column(name="provider_event_id",length=160) private String providerEventId; @Column(name="failure_code",length=80) private String failureCode;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    protected PaymentTransaction(){}
    public PaymentTransaction(UUID id,UUID paymentId,UUID attemptId,UUID refundId,TransactionType type,TransactionStatus status,
            BigDecimal amount,String currency,String providerTransactionId,String providerEventId,String failureCode,Instant at){this.id=id;this.paymentId=paymentId;this.attemptId=attemptId;this.refundId=refundId;this.type=type;this.status=status;this.amount=amount;this.currency=currency;this.providerTransactionId=providerTransactionId;this.providerEventId=providerEventId;this.failureCode=failureCode;occurredAt=at;}
    public UUID getId(){return id;} public UUID getPaymentId(){return paymentId;} public UUID getAttemptId(){return attemptId;} public UUID getRefundId(){return refundId;}
    public TransactionType getType(){return type;} public TransactionStatus getStatus(){return status;} public BigDecimal getAmount(){return amount;} public String getCurrency(){return currency;}
    public String getProviderTransactionId(){return providerTransactionId;} public String getProviderEventId(){return providerEventId;} public String getFailureCode(){return failureCode;} public Instant getOccurredAt(){return occurredAt;}
}
