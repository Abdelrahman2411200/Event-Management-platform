package com.eventplatform.event.outbox;

import com.eventplatform.contracts.CorrelationIds;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "platform.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxMessageRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final int batchSize;

    public OutboxRelay(
            OutboxMessageRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${platform.outbox.topic:event-platform.event-lifecycle.v1}") String topic,
            @Value("${platform.outbox.batch-size:50}") int batchSize) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${platform.outbox.publish-interval:1s}")
    @Transactional
    public void publishPending() {
        Instant now = Instant.now();
        List<OutboxMessage> messages = repository.findPending(now, PageRequest.of(0, batchSize));
        for (OutboxMessage message : messages) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        topic, message.getAggregateId().toString(), message.getPayload());
                header(record, "eventId", message.getId().toString());
                header(record, "eventType", message.getEventType());
                header(record, "eventVersion", Integer.toString(message.getEventVersion()));
                header(record, "occurredAt", message.getOccurredAt().toString());
                header(record, "producer", "event-service");
                header(record, CorrelationIds.KAFKA_HEADER, message.getCorrelationId());
                if (message.getTraceparent() != null) {
                    header(record, CorrelationIds.TRACEPARENT_HEADER, message.getTraceparent());
                }
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
                message.markPublished(Instant.now());
            } catch (Exception exception) {
                message.markFailed(exception.getMessage(), Instant.now());
                LOGGER.warn("Outbox publication failed for event {}", message.getId());
            }
        }
    }

    private void header(ProducerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
