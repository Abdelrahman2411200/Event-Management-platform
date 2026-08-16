package com.eventplatform.payment.integration;

import com.eventplatform.payment.domain.PaymentStatus;
import java.math.BigDecimal; import java.time.Instant; import java.util.*;

public final class PaymentLifecycleEvents {
 public static final String PROCESSING="event-platform.payment.processing.v1",SUCCEEDED="event-platform.payment.succeeded.v1",FAILED="event-platform.payment.failed.v1",REFUNDED="event-platform.refund.succeeded.v1",REFUND_FAILED="event-platform.refund.failed.v1";
 private PaymentLifecycleEvents(){}
 public record PaymentChangedV1(UUID paymentId,UUID bookingId,UUID attendeeId,UUID eventId,BigDecimal amount,String currency,PaymentStatus status,String failureCode,String failureReason,String eventTitle,Instant eventStartsAt,String attendeeEmail,String attendeePhone,String attendeeLocale,Instant occurredAt){}
 public record RefundChangedV1(UUID refundId,UUID paymentId,UUID bookingId,UUID attendeeId,UUID eventId,BigDecimal amount,String currency,List<UUID> ticketIds,boolean full,String failureCode,String failureReason,String eventTitle,Instant eventStartsAt,String attendeeEmail,String attendeePhone,String attendeeLocale,Instant occurredAt){}
}
