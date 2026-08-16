package com.eventplatform.attendee.outbox;

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
public class AttendeeOutboxRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttendeeOutboxRelay.class);
    private final AttendeeOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final int batchSize;
    private final int maxAttempts;
    private final Clock clock;
    private final Counter published;
    private final Counter failed;
    private final Counter deadLettered;

    public AttendeeOutboxRelay(
            AttendeeOutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${platform.outbox.topic:event-platform.attendee-lifecycle.v1}") String topic,
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
        this.published = meterRegistry.counter("platform.outbox.publications", "service", "attendee-service", "result", "published");
        this.failed = meterRegistry.counter("platform.outbox.publications", "service", "attendee-service", "result", "retry");
        this.deadLettered = meterRegistry.counter("platform.outbox.publications", "service", "attendee-service", "result", "dead-lettered");
    }

    @Scheduled(fixedDelayString = "${platform.outbox.publish-interval:1s}")
    @Transactional
    public void publishPending() {
        List<AttendeeOutboxMessage> messages = repository.findPending(clock.instant(), PageRequest.of(0, batchSize));
        for (AttendeeOutboxMessage message : messages) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        topic, message.getAggregateId().toString(), message.getPayload());
                KafkaEventHeaders.write(record.headers(), new KafkaEventMetadata(
                        message.getId(), message.getEventType(), message.getEventVersion(),
                        message.getOccurredAt(), message.getCorrelationId(), message.getTraceparent(),
                        "attendee-service", message.getAggregateType(), message.getAggregateId().toString()));
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
                message.markPublished(clock.instant());
                published.increment();
            } catch (Exception exception) {
                message.markFailed(exception.getMessage(), clock.instant(), maxAttempts);
                if (message.isDeadLettered()) {
                    deadLettered.increment();
                    LOGGER.error("Attendee outbox message dead-lettered messageId={} eventType={} aggregateId={} attempts={}",
                            message.getId(), message.getEventType(), message.getAggregateId(), message.getPublishAttempts());
                } else {
                    failed.increment();
                    LOGGER.warn("Attendee outbox publication scheduled for retry messageId={} eventType={} attempts={}",
                            message.getId(), message.getEventType(), message.getPublishAttempts());
                }
            }
        }
    }
}
