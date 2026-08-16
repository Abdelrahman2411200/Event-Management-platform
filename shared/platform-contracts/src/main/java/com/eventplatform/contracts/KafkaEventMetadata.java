package com.eventplatform.contracts;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;
import org.apache.kafka.common.header.Headers;

/** Domain-free metadata carried by every platform Kafka record. */
public record KafkaEventMetadata(
        UUID messageId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String correlationId,
        String traceparent,
        String producer,
        String aggregateType,
        String aggregateId) {

    public KafkaEventMetadata {
        Objects.requireNonNull(messageId, "messageId");
        requireText(eventType, "eventType");
        if (!eventType.matches("event-platform\\.[a-z0-9-]+\\.[a-z0-9-]+\\.v[1-9][0-9]*")) {
            throw new IllegalArgumentException("eventType does not follow the versioned platform convention");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        requireText(correlationId, "correlationId");
        requireText(producer, "producer");
        requireText(aggregateType, "aggregateType");
        requireText(aggregateId, "aggregateId");
    }

    public static KafkaEventMetadata from(Headers headers) {
        String canonicalId = KafkaEventHeaders.value(headers, KafkaEventHeaders.MESSAGE_ID);
        String legacyId = KafkaEventHeaders.value(headers, KafkaEventHeaders.LEGACY_EVENT_ID);
        String canonicalVersion = KafkaEventHeaders.value(headers, KafkaEventHeaders.SCHEMA_VERSION);
        String legacyVersion = KafkaEventHeaders.value(headers, KafkaEventHeaders.LEGACY_EVENT_VERSION);
        String messageId = firstText(canonicalId, legacyId);
        String schemaVersion = firstText(canonicalVersion, legacyVersion);
        try {
            UUID id = UUID.fromString(required(messageId, KafkaEventHeaders.MESSAGE_ID));
            String eventType = required(KafkaEventHeaders.value(headers, KafkaEventHeaders.EVENT_TYPE),
                    KafkaEventHeaders.EVENT_TYPE);
            Instant occurredAt = Instant.parse(required(
                    KafkaEventHeaders.value(headers, KafkaEventHeaders.OCCURRED_AT),
                    KafkaEventHeaders.OCCURRED_AT));
            String producer = defaultText(KafkaEventHeaders.value(headers, KafkaEventHeaders.PRODUCER), "legacy");
            String aggregateId = defaultText(
                    KafkaEventHeaders.value(headers, KafkaEventHeaders.AGGREGATE_ID), "unknown");
            String aggregateType = defaultText(
                    KafkaEventHeaders.value(headers, KafkaEventHeaders.AGGREGATE_TYPE), "Legacy");
            String correlationId = defaultText(
                    KafkaEventHeaders.value(headers, CorrelationIds.KAFKA_HEADER), "kafka:" + id);
            return new KafkaEventMetadata(
                    id,
                    eventType,
                    Integer.parseInt(required(schemaVersion, KafkaEventHeaders.SCHEMA_VERSION)),
                    occurredAt,
                    correlationId,
                    KafkaEventHeaders.value(headers, CorrelationIds.TRACEPARENT_HEADER),
                    producer,
                    aggregateType,
                    aggregateId);
        } catch (DateTimeParseException | NumberFormatException exception) {
            throw new IllegalArgumentException("Kafka integration metadata is malformed", exception);
        }
    }

    private static String required(String value, String name) {
        requireText(value, name);
        return value;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static String firstText(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
