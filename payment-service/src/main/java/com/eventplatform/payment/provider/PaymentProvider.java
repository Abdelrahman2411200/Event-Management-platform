package com.eventplatform.payment.provider;

import java.math.BigDecimal;

public interface PaymentProvider {
    String name();
    ProviderResult createPayment(CreatePayment command);
    ProviderResult verifyPayment(String providerPaymentId);
    ProviderResult refund(RefundPayment command);
    VerifiedWebhook verifyWebhook(String payload,String signature);

    record CreatePayment(String idempotencyKey,BigDecimal amount,String currency,String paymentMethodToken){}
    record RefundPayment(String providerPaymentId,String idempotencyKey,BigDecimal amount,String currency){}
    enum Outcome { PROCESSING, SUCCEEDED, FAILED }
    record ProviderResult(String providerObjectId,String transactionId,Outcome outcome,String failureCode,String failureReason){}
    record VerifiedWebhook(String eventId,String eventType,String providerPaymentId,Outcome outcome){}
}
