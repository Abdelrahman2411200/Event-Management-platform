package com.eventplatform.payment.outbox;

import com.eventplatform.payment.api.PaymentApiException;
import com.eventplatform.payment.api.RequestContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PaymentTransactionalOutbox {
    private final PaymentOutboxRepository repository; private final ObjectMapper mapper;
    public PaymentTransactionalOutbox(PaymentOutboxRepository repository,ObjectMapper mapper){this.repository=repository;this.mapper=mapper;}
    public void append(String aggregateType,UUID aggregateId,String eventType,Object payload,RequestContext context,Instant at){try{repository.save(new PaymentOutboxMessage(UUID.randomUUID(),aggregateType,aggregateId,eventType,1,mapper.writeValueAsString(payload),context.correlationId(),context.traceparent(),at));}catch(JsonProcessingException e){throw new PaymentApiException(HttpStatus.INTERNAL_SERVER_ERROR,"OUTBOX_SERIALIZATION_FAILED","The payment state change could not be recorded");}}
}
