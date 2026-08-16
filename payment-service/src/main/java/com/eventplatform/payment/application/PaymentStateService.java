package com.eventplatform.payment.application;

import com.eventplatform.payment.api.*;
import com.eventplatform.payment.domain.*;
import com.eventplatform.payment.integration.PaymentLifecycleEvents;
import com.eventplatform.payment.outbox.PaymentTransactionalOutbox;
import com.eventplatform.payment.provider.PaymentProvider;
import java.time.*; import java.util.*;
import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentStateService {
 public record ClaimedPayment(Payment payment,PaymentAttempt attempt,boolean existing){}
 private final BookingPaymentOrderRepository orders; private final PaymentRepository payments; private final PaymentAttemptRepository attempts;
 private final PaymentTransactionRepository transactions; private final ProviderWebhookEventRepository webhooks; private final PaymentTransactionalOutbox outbox; private final Clock clock;
 public PaymentStateService(BookingPaymentOrderRepository orders,PaymentRepository payments,PaymentAttemptRepository attempts,PaymentTransactionRepository transactions,ProviderWebhookEventRepository webhooks,PaymentTransactionalOutbox outbox,Clock clock){this.orders=orders;this.payments=payments;this.attempts=attempts;this.transactions=transactions;this.webhooks=webhooks;this.outbox=outbox;this.clock=clock;}
 @Transactional public ClaimedPayment claim(UUID bookingId,UUID actor,String key,String fingerprint,String provider){
  validateKey(key);BookingPaymentOrder order=orders.findByIdForUpdate(bookingId).orElseThrow(()->new PaymentApiException(HttpStatus.CONFLICT,"PAYMENT_ORDER_NOT_READY","The booking payment order has not arrived yet"));
  if(!order.getAttendeeId().equals(actor))throw new PaymentApiException(HttpStatus.FORBIDDEN,"PAYMENT_OWNERSHIP_REQUIRED","Only the booking owner may pay for this booking");
  PaymentAttempt prior=attempts.findByAttendeeIdAndIdempotencyKey(actor,key).orElse(null);if(prior!=null){if(!prior.getPaymentMethodFingerprint().equals(fingerprint))throw new PaymentApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","The idempotency key was already used with different payment input");Payment payment=payments.findById(prior.getPaymentId()).orElseThrow();if(!payment.getBookingId().equals(bookingId))throw new PaymentApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","The idempotency key belongs to another booking");return new ClaimedPayment(payment,prior,true);}
  Instant now=clock.instant();if(!order.getHoldExpiresAt().isAfter(now))throw new PaymentApiException(HttpStatus.CONFLICT,"BOOKING_HOLD_EXPIRED","The inventory hold expired before payment started");
  Payment payment=payments.findByBookingId(bookingId).orElseGet(()->payments.save(new Payment(UUID.randomUUID(),order,provider,now)));
  if(payment.getStatus()!=PaymentStatus.CREATED&&payment.getStatus()!=PaymentStatus.PROCESSING)throw new PaymentApiException(HttpStatus.CONFLICT,"PAYMENT_ALREADY_FINAL","The booking payment is already final");
  PaymentAttempt attempt=attempts.save(new PaymentAttempt(UUID.randomUUID(),payment.getId(),actor,key,fingerprint,now));payment.processing(null,now);
  return new ClaimedPayment(payment,attempt,false);
 }
 @Transactional public Payment apply(UUID paymentId,UUID attemptId,PaymentProvider.ProviderResult result,RequestContext context,String providerEventId){
  Payment payment=payments.findByIdForUpdate(paymentId).orElseThrow();PaymentAttempt attempt=attempts.findById(attemptId).orElseThrow();Instant now=clock.instant();
  if ((payment.getStatus()==PaymentStatus.SUCCEEDED || payment.getStatus()==PaymentStatus.PARTIALLY_REFUNDED || payment.getStatus()==PaymentStatus.REFUNDED)
          && result.outcome()!=PaymentProvider.Outcome.SUCCEEDED) return payment;
  if (payment.getStatus()==PaymentStatus.FAILED && result.outcome()!=PaymentProvider.Outcome.FAILED) return payment;
  if ((payment.getStatus()==PaymentStatus.SUCCEEDED || payment.getStatus()==PaymentStatus.PARTIALLY_REFUNDED || payment.getStatus()==PaymentStatus.REFUNDED)
          && result.outcome()==PaymentProvider.Outcome.SUCCEEDED) return payment;
  if(result.outcome()==PaymentProvider.Outcome.PROCESSING){payment.processing(result.providerObjectId(),now);attempt.processing(result.providerObjectId(),now);append(payment,PaymentLifecycleEvents.PROCESSING,context,now);transaction(payment,attemptId,null,TransactionType.PAYMENT,TransactionStatus.PROCESSING,result,providerEventId,now);}
  else if(result.outcome()==PaymentProvider.Outcome.SUCCEEDED){attempt.succeed(result.providerObjectId(),now);if(payment.succeed(result.providerObjectId(),now)){append(payment,PaymentLifecycleEvents.SUCCEEDED,context,now);transaction(payment,attemptId,null,TransactionType.PAYMENT,TransactionStatus.SUCCEEDED,result,providerEventId,now);}}
  else {attempt.fail(result.providerObjectId(),result.failureCode(),result.failureReason(),now);if(payment.fail(result.failureCode(),result.failureReason(),now)){append(payment,PaymentLifecycleEvents.FAILED,context,now);transaction(payment,attemptId,null,TransactionType.PAYMENT,TransactionStatus.FAILED,result,providerEventId,now);}}
  return payment;
 }
 @Transactional public boolean applyWebhook(String provider,PaymentProvider.VerifiedWebhook hook,String fingerprint,RequestContext context,PaymentProvider.ProviderResult result){
  if(webhooks.findByProviderAndProviderEventId(provider,hook.eventId()).isPresent())return false;Instant now=clock.instant();ProviderWebhookEvent event=webhooks.save(new ProviderWebhookEvent(UUID.randomUUID(),provider,hook.eventId(),fingerprint,hook.eventType(),now));
  Payment payment=payments.findByProviderPaymentId(hook.providerPaymentId()).orElse(null);if(payment==null){event.ignored(now);return true;}
  PaymentAttempt attempt=attempts.findAllByPaymentIdOrderByCreatedAt(payment.getId()).stream().findFirst().orElseThrow();apply(payment.getId(),attempt.getId(),result,context,hook.eventId());event.processed(now);return true;
 }
 private void append(Payment p,String type,RequestContext context,Instant now){BookingPaymentOrder order=orders.findById(p.getBookingId()).orElseThrow();outbox.append("Payment",p.getId(),type,new PaymentLifecycleEvents.PaymentChangedV1(p.getId(),p.getBookingId(),p.getAttendeeId(),p.getEventId(),p.getAmount(),p.getCurrency(),p.getStatus(),p.getFailureCode(),p.getFailureReason(),order.getEventTitle(),order.getEventStartsAt(),order.getAttendeeEmail(),order.getAttendeePhone(),order.getAttendeeLocale(),now),context,now);}
 private void transaction(Payment p,UUID attemptId,UUID refundId,TransactionType type,TransactionStatus status,PaymentProvider.ProviderResult r,String providerEventId,Instant now){transactions.save(new PaymentTransaction(UUID.randomUUID(),p.getId(),attemptId,refundId,type,status,p.getAmount(),p.getCurrency(),r.transactionId(),providerEventId,r.failureCode(),now));}
 private void validateKey(String key){if(key==null||key.isBlank()||key.length()>128)throw new PaymentApiException(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_KEY_REQUIRED","A valid Idempotency-Key header is required");}
}
