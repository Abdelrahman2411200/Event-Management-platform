package com.eventplatform.payment.outbox;

import com.eventplatform.contracts.CorrelationIds;
import java.nio.charset.StandardCharsets; import java.time.Instant; import java.util.List; import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord; import org.slf4j.*; import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.data.domain.PageRequest; import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component; import org.springframework.transaction.annotation.Transactional;

@Component @ConditionalOnProperty(name="platform.outbox.enabled",havingValue="true",matchIfMissing=true)
public class PaymentOutboxRelay {
 private static final Logger LOG=LoggerFactory.getLogger(PaymentOutboxRelay.class); private final PaymentOutboxRepository repository; private final KafkaTemplate<String,String> kafka; private final String topic; private final int batch;
 public PaymentOutboxRelay(PaymentOutboxRepository repository,KafkaTemplate<String,String> kafka,@Value("${platform.outbox.topic:event-platform.payment-lifecycle.v1}") String topic,@Value("${platform.outbox.batch-size:50}") int batch){this.repository=repository;this.kafka=kafka;this.topic=topic;this.batch=batch;}
 @Scheduled(fixedDelayString="${platform.outbox.publish-interval:1s}") @Transactional public void publishPending(){for(PaymentOutboxMessage m:repository.findPending(Instant.now(),PageRequest.of(0,batch))){try{ProducerRecord<String,String> r=new ProducerRecord<>(topic,m.getAggregateId().toString(),m.getPayload());header(r,"eventId",m.getId().toString());header(r,"eventType",m.getEventType());header(r,"eventVersion","1");header(r,"occurredAt",m.getOccurredAt().toString());header(r,"producer","payment-service");header(r,CorrelationIds.KAFKA_HEADER,m.getCorrelationId());if(m.getTraceparent()!=null)header(r,CorrelationIds.TRACEPARENT_HEADER,m.getTraceparent());kafka.send(r).get(10,TimeUnit.SECONDS);m.markPublished(Instant.now());}catch(Exception e){m.markFailed(e.getMessage(),Instant.now());LOG.warn("Payment outbox publication failed for event {}",m.getId());}}}
 private void header(ProducerRecord<String,String> r,String name,String value){r.headers().add(name,value.getBytes(StandardCharsets.UTF_8));}
}
