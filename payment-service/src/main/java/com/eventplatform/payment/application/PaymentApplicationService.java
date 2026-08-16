package com.eventplatform.payment.application;

import com.eventplatform.payment.api.*; import com.eventplatform.payment.domain.*; import com.eventplatform.payment.provider.PaymentProvider;
import java.util.UUID; import org.springframework.stereotype.Service;

@Service
public class PaymentApplicationService {
 private final PaymentStateService state; private final PaymentProvider provider; private final PaymentAttemptRepository attempts;
 public PaymentApplicationService(PaymentStateService state,PaymentProvider provider,PaymentAttemptRepository attempts){this.state=state;this.provider=provider;this.attempts=attempts;}
 public Payment process(UUID bookingId,UUID actor,String key,String token,RequestContext context){
  if(token==null||token.isBlank()||token.length()>128)throw new PaymentApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"PAYMENT_METHOD_TOKEN_REQUIRED","A provider payment method token is required");
  String fingerprint=SensitiveValueFingerprint.sha256(token);PaymentStateService.ClaimedPayment claim=state.claim(bookingId,actor,key,fingerprint,provider.name());
  if(claim.existing())return claim.payment();PaymentProvider.ProviderResult result=provider.createPayment(new PaymentProvider.CreatePayment(key,claim.payment().getAmount(),claim.payment().getCurrency(),token));
  return state.apply(claim.payment().getId(),claim.attempt().getId(),result,context,null);
 }
 public Payment verify(Payment payment,RequestContext context){PaymentAttempt attempt=stateAttempt(payment);return state.apply(payment.getId(),attempt.getId(),provider.verifyPayment(payment.getProviderPaymentId()),context,null);}
 private PaymentAttempt stateAttempt(Payment payment){return attempts.findAllByPaymentIdOrderByCreatedAt(payment.getId()).stream().findFirst().orElseThrow();}
}
