package com.eventplatform.notification.integration;

import com.eventplatform.contracts.KafkaEventMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {
    private final NotificationEventHandler handler;
    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(NotificationEventHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                "${platform.integration-events.event-topic:event-platform.event-lifecycle.v1}",
                "${platform.integration-events.attendee-topic:event-platform.attendee-lifecycle.v1}",
                "${platform.integration-events.payment-topic:event-platform.payment-lifecycle.v1}"
            },
            groupId = "${spring.kafka.consumer.group-id:notification-service-v1}",
            autoStartup = "${platform.integration-events.enabled:true}")
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        handler.handle(KafkaEventMetadata.from(record.headers()), objectMapper.readTree(record.value()));
    }
}
