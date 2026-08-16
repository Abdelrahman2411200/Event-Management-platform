package com.eventplatform.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity @Table(name="refunds")
public class Refund {
    @Id private UUID id; @Column(name="payment_id",nullable=false) private UUID paymentId; @Column(name="booking_id",nullable=false) private UUID bookingId;
    @Column(name="requested_by",nullable=false) private UUID requestedBy; @Column(name="idempotency_key",nullable=false,length=128) private String idempotencyKey;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount; @Column(nullable=false,length=3) private String currency;
    @Column(name="ticket_ids") private String ticketIds; @Column(length=500) private String reason; @Column(name="provider_refund_id",length=160) private String providerRefundId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private RefundStatus status; @Column(name="failure_code",length=80) private String failureCode;
    @Column(name="failure_reason",length=500) private String failureReason; @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt; @Column(name="completed_at") private Instant completedAt; @Version private long version;
    protected Refund(){}
    public Refund(UUID id,UUID paymentId,UUID bookingId,UUID actor,String key,BigDecimal amount,String currency,List<UUID> tickets,String reason,Instant now){this.id=id;this.paymentId=paymentId;this.bookingId=bookingId;requestedBy=actor;idempotencyKey=key;this.amount=amount;this.currency=currency;ticketIds=tickets==null?null:tickets.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));this.reason=reason;status=RefundStatus.PROCESSING;createdAt=now;updatedAt=now;}
    public void succeed(String providerId,Instant now){providerRefundId=providerId;status=RefundStatus.SUCCEEDED;completedAt=now;updatedAt=now;}
    public void fail(String providerId,String code,String message,Instant now){providerRefundId=providerId;status=RefundStatus.FAILED;failureCode=code;failureReason=message;completedAt=now;updatedAt=now;}
    public List<UUID> ticketIds(){return ticketIds==null||ticketIds.isBlank()?List.of():Arrays.stream(ticketIds.split(",")).map(UUID::fromString).toList();}
    public UUID getId(){return id;} public UUID getPaymentId(){return paymentId;} public UUID getBookingId(){return bookingId;} public UUID getRequestedBy(){return requestedBy;}
    public String getIdempotencyKey(){return idempotencyKey;} public BigDecimal getAmount(){return amount;} public String getCurrency(){return currency;} public String getReason(){return reason;}
    public String getProviderRefundId(){return providerRefundId;} public RefundStatus getStatus(){return status;} public String getFailureCode(){return failureCode;} public String getFailureReason(){return failureReason;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
