package com.eventplatform.event.integration;

import com.eventplatform.contracts.KafkaEventMetadata;
import com.eventplatform.event.api.RequestContext;
import com.eventplatform.event.application.InventorySagaCommandService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "platform.integration-events.enabled", havingValue = "true", matchIfMissing = true)
public class AttendeeInventorySagaConsumer {
    private static final String CONFIRM = "event-platform.inventory.confirmation-requested.v1";
    private static final String RELEASE = "event-platform.inventory.release-requested.v1";

    private final ProcessedIntegrationEventRepository processed;
    private final InventorySagaCommandService service;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AttendeeInventorySagaConsumer(
            ProcessedIntegrationEventRepository processed,
            InventorySagaCommandService service,
            ObjectMapper mapper,
            Clock clock) {
        this.processed = processed;
        this.service = service;
        this.mapper = mapper;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${platform.integration-events.attendee-topic:event-platform.attendee-lifecycle.v1}",
            groupId = "${spring.kafka.consumer.group-id:event-service-inventory-saga-v1}")
    @Transactional
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        KafkaEventMetadata metadata = KafkaEventMetadata.from(record.headers());
        if (metadata.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported event schema version " + metadata.schemaVersion());
        }
        UUID messageId = metadata.messageId();
        if (processed.existsById(messageId)) return;
        if (CONFIRM.equals(metadata.eventType()) || RELEASE.equals(metadata.eventType())) {
            JsonNode body = mapper.readTree(record.value());
            InventorySagaCommandService.Command command = new InventorySagaCommandService.Command(
                    uuid(body, "bookingId"), uuidOrNull(body, "paymentId"), uuid(body, "attendeeId"),
                    uuid(body, "eventId"), uuid(body, "ticketTypeId"), uuid(body, "inventoryReservationId"),
                    body.required("quantity").asInt(), body.required("commandKey").asText());
            RequestContext context = new RequestContext(metadata.correlationId(), metadata.traceparent());
            if (CONFIRM.equals(metadata.eventType())) service.confirm(command, context);
            else service.release(command, context);
        }
        processed.save(new ProcessedIntegrationEvent(messageId, metadata.eventType(), clock.instant()));
    }

    private UUID uuid(JsonNode node, String name) { return UUID.fromString(node.required(name).asText()); }
    private UUID uuidOrNull(JsonNode node, String name) {
        return node.path(name).isNull() || node.path(name).isMissingNode()
                ? null : UUID.fromString(node.path(name).asText());
    }
}
