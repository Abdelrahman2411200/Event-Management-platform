package com.eventplatform.payment.application;

import com.eventplatform.payment.api.RequestContext; import com.eventplatform.payment.config.PaymentProperties; import com.eventplatform.payment.domain.*; import com.eventplatform.payment.provider.PaymentProvider; import java.time.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;

@Component @ConditionalOnProperty(name="platform.payment.reconciliation-enabled",havingValue="true",matchIfMissing=true)
public class PaymentReconciliationJob {
 private final PaymentRepository payments; private final PaymentAttemptRepository attempts; private final PaymentProvider provider; private final PaymentStateService state; private final PaymentProperties properties; private final Clock clock;
 public PaymentReconciliationJob(PaymentRepository payments,PaymentAttemptRepository attempts,PaymentProvider provider,PaymentStateService state,PaymentProperties properties,Clock clock){this.payments=payments;this.attempts=attempts;this.provider=provider;this.state=state;this.properties=properties;this.clock=clock;}
 @Scheduled(fixedDelayString="${platform.payment.reconciliation-delay:10s}") public void reconcile(){Instant before=clock.instant().minus(properties.processingTimeout());for(Payment payment:payments.findAllByStatusAndUpdatedAtBefore(PaymentStatus.PROCESSING,before)){PaymentAttempt attempt=attempts.findAllByPaymentIdOrderByCreatedAt(payment.getId()).stream().findFirst().orElse(null);if(attempt!=null&&payment.getProviderPaymentId()!=null)state.apply(payment.getId(),attempt.getId(),provider.verifyPayment(payment.getProviderPaymentId()),RequestContext.system("payment-reconcile",payment.getId()),null);}}
}
