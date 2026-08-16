package com.eventplatform.event.outbox;

import com.eventplatform.contracts.KafkaEventHeaders;
import com.eventplatform.contracts.KafkaEventMetadata;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
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
    private final int maxAttempts;
    private final Clock clock;
    private final Counter published;
    private final Counter failed;
    private final Counter deadLettered;

    public OutboxRelay(
            OutboxMessageRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${platform.outbox.topic:event-platform.event-lifecycle.v1}") String topic,
            @Value("${platform.outbox.batch-size:50}") int batchSize,
            @Value("${platform.outbox.max-attempts:10}") int maxAttempts,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
        this.published = meterRegistry.counter("platform.outbox.publications", "service", "event-service", "result", "published");
        this.failed = meterRegistry.counter("platform.outbox.publications", "service", "event-service", "result", "retry");
        this.deadLettered = meterRegistry.counter("platform.outbox.publications", "service", "event-service", "result", "dead-lettered");
    }

    @Scheduled(fixedDelayString = "${platform.outbox.publish-interval:1s}")
    @Transactional
    public void publishPending() {
        Instant now = clock.instant();
        List<OutboxMessage> messages = repository.findPending(now, PageRequest.of(0, batchSize));
        for (OutboxMessage message : messages) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        topic, message.getAggregateId().toString(), message.getPayload());
                KafkaEventHeaders.write(record.headers(), new KafkaEventMetadata(
                        message.getId(), message.getEventType(), message.getEventVersion(),
                        message.getOccurredAt(), message.getCorrelationId(), message.getTraceparent(),
                        "event-service", message.getAggregateType(), message.getAggregateId().toString()));
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
                message.markPublished(clock.instant());
                published.increment();
            } catch (Exception exception) {
                message.markFailed(exception.getMessage(), clock.instant(), maxAttempts);
                if (message.isDeadLettered()) {
                    deadLettered.increment();
                    LOGGER.error("Outbox message dead-lettered messageId={} eventType={} aggregateId={} attempts={}",
                            message.getId(), message.getEventType(), message.getAggregateId(), message.getPublishAttempts());
                } else {
                    failed.increment();
                    LOGGER.warn("Outbox publication scheduled for retry messageId={} eventType={} attempts={}",
                            message.getId(), message.getEventType(), message.getPublishAttempts());
                }
            }
        }
    }
}
