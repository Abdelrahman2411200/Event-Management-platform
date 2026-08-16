package com.eventplatform.payment.application;

import com.eventplatform.payment.api.*; import com.eventplatform.payment.provider.PaymentProvider; import org.springframework.stereotype.Service;

@Service
public class PaymentWebhookService {
 private final PaymentProvider provider; private final PaymentStateService state;
 public PaymentWebhookService(PaymentProvider provider,PaymentStateService state){this.provider=provider;this.state=state;}
 public boolean consume(String providerName,String payload,String signature,RequestContext context){if(!provider.name().equals(providerName))throw new PaymentApiException(org.springframework.http.HttpStatus.NOT_FOUND,"PAYMENT_PROVIDER_NOT_FOUND","The payment provider is not configured");PaymentProvider.VerifiedWebhook hook=provider.verifyWebhook(payload,signature);PaymentProvider.ProviderResult result=new PaymentProvider.ProviderResult(hook.providerPaymentId(),hook.providerPaymentId(),hook.outcome(),hook.outcome()==PaymentProvider.Outcome.FAILED?"PROVIDER_REPORTED_FAILURE":null,hook.outcome()==PaymentProvider.Outcome.FAILED?"The provider reported a failed payment":null);return state.applyWebhook(providerName,hook,SensitiveValueFingerprint.sha256(payload),context,result);}
}
