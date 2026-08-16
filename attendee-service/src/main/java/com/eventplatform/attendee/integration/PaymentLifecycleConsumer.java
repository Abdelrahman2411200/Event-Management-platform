package com.eventplatform.attendee.integration;

import com.eventplatform.attendee.api.RequestContext;
import com.eventplatform.attendee.application.BookingSagaService;
import com.eventplatform.contracts.KafkaEventMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "platform.integration-events.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentLifecycleConsumer {
    private static final String PROCESSING = "event-platform.payment.processing.v1";
    private static final String SUCCEEDED = "event-platform.payment.succeeded.v1";
    private static final String FAILED = "event-platform.payment.failed.v1";
    private static final String REFUNDED = "event-platform.refund.succeeded.v1";

    private final ProcessedIntegrationEventRepository processed;
    private final BookingSagaService saga;
    private final ObjectMapper mapper;
    private final Clock clock;

    public PaymentLifecycleConsumer(
            ProcessedIntegrationEventRepository processed,
            BookingSagaService saga,
            ObjectMapper mapper,
            Clock clock) {
        this.processed = processed;
        this.saga = saga;
        this.mapper = mapper;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${platform.integration-events.payment-topic:event-platform.payment-lifecycle.v1}",
            groupId = "${spring.kafka.consumer.group-id:attendee-service-v1}")
    @Transactional
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        KafkaEventMetadata metadata = KafkaEventMetadata.from(record.headers());
        if (metadata.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported event schema version " + metadata.schemaVersion());
        }
        UUID messageId = metadata.messageId();
        if (processed.existsById(messageId)) return;
        JsonNode body = mapper.readTree(record.value());
        RequestContext context = new RequestContext(metadata.correlationId(), metadata.traceparent(), null);
        switch (metadata.eventType()) {
            case PROCESSING -> saga.paymentProcessing(
                    uuid(body, "bookingId"), uuid(body, "paymentId"), uuid(body, "attendeeId"),
                    money(body, "amount"), body.required("currency").asText());
            case SUCCEEDED -> saga.paymentSucceeded(
                    uuid(body, "bookingId"), uuid(body, "paymentId"), uuid(body, "attendeeId"),
                    money(body, "amount"), body.required("currency").asText(), context);
            case FAILED -> saga.paymentFailed(
                    uuid(body, "bookingId"), uuid(body, "paymentId"), uuid(body, "attendeeId"),
                    text(body, "failureCode"), text(body, "failureReason"), context);
            case REFUNDED -> saga.refunded(
                    uuid(body, "bookingId"), uuid(body, "paymentId"), uuid(body, "attendeeId"),
                    money(body, "amount"), body.required("currency").asText(), ids(body.path("ticketIds")),
                    body.path("full").asBoolean(), context);
            default -> { }
        }
        processed.save(new ProcessedIntegrationEvent(messageId, metadata.eventType(), clock.instant()));
    }

    private UUID uuid(JsonNode node, String name) { return UUID.fromString(node.required(name).asText()); }
    private BigDecimal money(JsonNode node, String name) { return new BigDecimal(node.required(name).asText()); }
    private String text(JsonNode node, String name) { return node.path(name).isNull() ? null : node.path(name).asText(null); }
    private List<UUID> ids(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<UUID> result = new ArrayList<>();
        node.forEach(value -> result.add(UUID.fromString(value.asText())));
        return result;
    }
}
