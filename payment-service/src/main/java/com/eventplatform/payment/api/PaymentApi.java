package com.eventplatform.payment.api;

import com.eventplatform.payment.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal; import java.time.Instant; import java.util.*;

public final class PaymentApi {
 private PaymentApi(){}
 public record CreatePaymentRequest(@NotNull UUID bookingId,@NotBlank @Size(max=128) @Schema(description="Opaque provider token; never persisted",example="sandbox-success") String paymentMethodToken){}
 public record RefundRequest(List<UUID> ticketIds,@Size(max=500) String reason){}
 public record PaymentResponse(UUID id,UUID bookingId,UUID attendeeId,UUID eventId,BigDecimal amount,BigDecimal refundedAmount,String currency,String provider,String providerPaymentId,PaymentStatus status,String failureCode,String failureReason,Instant createdAt,Instant updatedAt,Instant paidAt,List<AttemptResponse> attempts,List<RefundResponse> refunds){}
 public record AttemptResponse(UUID id,PaymentAttemptStatus status,String providerAttemptId,String failureCode,String failureReason,Instant createdAt,Instant updatedAt){}
 public record RefundResponse(UUID id,BigDecimal amount,String currency,List<UUID> ticketIds,RefundStatus status,String providerRefundId,String failureCode,String failureReason,Instant createdAt,Instant updatedAt){}
 public record TransactionResponse(UUID id,TransactionType type,TransactionStatus status,BigDecimal amount,String currency,String providerTransactionId,String providerEventId,String failureCode,Instant occurredAt){}
 public record WebhookResponse(String status){}
}
