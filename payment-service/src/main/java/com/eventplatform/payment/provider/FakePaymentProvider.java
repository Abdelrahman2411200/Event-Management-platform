package com.eventplatform.payment.provider;

import com.eventplatform.payment.api.PaymentApiException;
import com.eventplatform.payment.config.PaymentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class FakePaymentProvider implements PaymentProvider {
    private static final Logger LOG=LoggerFactory.getLogger(FakePaymentProvider.class);
    private enum Behavior { SUCCESS, SUCCESS_REFUND_FAIL_ONCE, FAILURE, PROCESSING, PROCESSING_THEN_SUCCESS, PROCESSING_THEN_FAILURE }
    private final ConcurrentHashMap<String,Behavior> behaviorByPayment=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Integer> verificationCount=new ConcurrentHashMap<>();
    private final java.util.Set<String> refundFailuresRemaining=ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper; private final byte[] webhookSecret;
    public FakePaymentProvider(ObjectMapper mapper,PaymentProperties properties){this.mapper=mapper;String configured=properties.webhookSecret();if(configured==null||configured.isBlank()){configured=UUID.randomUUID().toString();LOG.warn("PAYMENT_WEBHOOK_SECRET is unset; generated an ephemeral local fake-provider secret");}webhookSecret=configured.getBytes(StandardCharsets.UTF_8);}
    @Override public String name(){return "fake";}
    @Override public ProviderResult createPayment(CreatePayment c){
        Behavior behavior=switch(c.paymentMethodToken()){case "sandbox-success"->Behavior.SUCCESS;case "sandbox-success-refund-fail-once"->Behavior.SUCCESS_REFUND_FAIL_ONCE;case "sandbox-failure"->Behavior.FAILURE;case "sandbox-processing"->Behavior.PROCESSING;case "sandbox-processing-then-success"->Behavior.PROCESSING_THEN_SUCCESS;case "sandbox-processing-then-failure"->Behavior.PROCESSING_THEN_FAILURE;default->throw new PaymentApiException(HttpStatus.UNPROCESSABLE_ENTITY,"PAYMENT_METHOD_TOKEN_INVALID","Use a documented sandbox payment token");};
        String id=stable("pay",c.idempotencyKey());behaviorByPayment.putIfAbsent(id,behavior);if(behavior==Behavior.SUCCESS_REFUND_FAIL_ONCE)refundFailuresRemaining.add(id);return result(id,behavior,false);
    }
    @Override public ProviderResult verifyPayment(String id){Behavior behavior=behaviorByPayment.get(id);if(behavior==null)return new ProviderResult(id,null,Outcome.FAILED,"PROVIDER_PAYMENT_NOT_FOUND","The fake provider payment does not exist");int count=verificationCount.merge(id,1,Integer::sum);return result(id,behavior,count>0);}
    @Override public ProviderResult refund(RefundPayment c){String id=stable("refund",c.idempotencyKey());if(refundFailuresRemaining.remove(c.providerPaymentId()))return new ProviderResult(id,id,Outcome.FAILED,"REFUND_PROVIDER_TEMPORARY_FAILURE","The sandbox provider rejected this refund attempt temporarily");return new ProviderResult(id,id,Outcome.SUCCEEDED,null,null);}
    @Override public VerifiedWebhook verifyWebhook(String payload,String signature){
        if(signature==null||!MessageDigest.isEqual(hmac(payload),decodeHex(signature)))throw new PaymentApiException(HttpStatus.UNAUTHORIZED,"WEBHOOK_SIGNATURE_INVALID","The provider webhook signature is invalid");
        try{JsonNode n=mapper.readTree(payload);return new VerifiedWebhook(n.required("eventId").asText(),n.required("eventType").asText(),n.required("providerPaymentId").asText(),Outcome.valueOf(n.required("status").asText()));}catch(Exception e){throw new PaymentApiException(HttpStatus.BAD_REQUEST,"WEBHOOK_PAYLOAD_INVALID","The provider webhook payload is invalid");}
    }
    private ProviderResult result(String id,Behavior b,boolean verified){return switch(b){case SUCCESS,SUCCESS_REFUND_FAIL_ONCE->new ProviderResult(id,id,Outcome.SUCCEEDED,null,null);case FAILURE->new ProviderResult(id,id,Outcome.FAILED,"PAYMENT_DECLINED","The sandbox payment was declined");case PROCESSING->new ProviderResult(id,null,Outcome.PROCESSING,null,null);case PROCESSING_THEN_SUCCESS->verified?new ProviderResult(id,id,Outcome.SUCCEEDED,null,null):new ProviderResult(id,null,Outcome.PROCESSING,null,null);case PROCESSING_THEN_FAILURE->verified?new ProviderResult(id,id,Outcome.FAILED,"PAYMENT_DECLINED","The sandbox payment was declined during verification"):new ProviderResult(id,null,Outcome.PROCESSING,null,null);};}
    private String stable(String prefix,String key){return prefix+"_"+UUID.nameUUIDFromBytes((prefix+":"+key).getBytes(StandardCharsets.UTF_8));}
    private byte[] hmac(String payload){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(webhookSecret,"HmacSHA256"));return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    private byte[] decodeHex(String value){try{return HexFormat.of().parseHex(value);}catch(IllegalArgumentException e){return new byte[0];}}
}
