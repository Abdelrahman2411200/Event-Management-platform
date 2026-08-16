package com.eventplatform.attendee.integration;

import com.eventplatform.attendee.api.RequestContext;
import com.eventplatform.attendee.application.BookingPersistenceService;
import com.eventplatform.contracts.CorrelationIds;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "platform.integration-events.enabled", havingValue = "true", matchIfMissing = true)
public class EventLifecycleConsumer {
    private static final String INVENTORY_EXPIRED = "event-platform.inventory.expired.v1";
    private static final String EVENT_CANCELLED = "event-platform.event.cancelled.v1";

    private final ProcessedIntegrationEventRepository processedRepository;
    private final BookingPersistenceService persistenceService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EventLifecycleConsumer(
            ProcessedIntegrationEventRepository processedRepository,
            BookingPersistenceService persistenceService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.processedRepository = processedRepository;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${platform.integration-events.event-topic:event-platform.event-lifecycle.v1}",
            groupId = "${spring.kafka.consumer.group-id:attendee-service-v1}")
    @Transactional
    public void consume(
            @Payload String payload,
            @Header("eventId") String messageId,
            @Header("eventType") String eventType,
            @Header(name = CorrelationIds.KAFKA_HEADER, required = false) String correlationId,
            @Header(name = CorrelationIds.TRACEPARENT_HEADER, required = false) String traceparent) throws Exception {
        UUID eventMessageId = UUID.fromString(messageId);
        if (processedRepository.existsById(eventMessageId)) return;
        JsonNode body = objectMapper.readTree(payload);
        RequestContext context = new RequestContext(
                correlationId == null ? "kafka:" + eventMessageId : correlationId,
                traceparent,
                null);
        if (INVENTORY_EXPIRED.equals(eventType)) {
            persistenceService.expireByReservation(UUID.fromString(body.required("reservationId").asText()), context);
        } else if (EVENT_CANCELLED.equals(eventType)) {
            persistenceService.cancelEvent(UUID.fromString(body.required("eventId").asText()), context);
        }
        processedRepository.save(new ProcessedIntegrationEvent(eventMessageId, eventType, clock.instant()));
    }
}
