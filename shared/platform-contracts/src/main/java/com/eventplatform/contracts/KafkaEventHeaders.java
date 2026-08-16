package com.eventplatform.contracts;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

/** Canonical Kafka integration-message headers with Phase 5 aliases for rolling upgrades. */
public final class KafkaEventHeaders {

    public static final String MESSAGE_ID = "messageId";
    public static final String LEGACY_EVENT_ID = "eventId";
    public static final String EVENT_TYPE = "eventType";
    public static final String SCHEMA_VERSION = "schemaVersion";
    public static final String LEGACY_EVENT_VERSION = "eventVersion";
    public static final String OCCURRED_AT = "occurredAt";
    public static final String PRODUCER = "producer";
    public static final String AGGREGATE_TYPE = "aggregateType";
    public static final String AGGREGATE_ID = "aggregateId";

    private KafkaEventHeaders() {
    }

    public static void write(Headers headers, KafkaEventMetadata metadata) {
        add(headers, MESSAGE_ID, metadata.messageId().toString());
        add(headers, LEGACY_EVENT_ID, metadata.messageId().toString());
        add(headers, EVENT_TYPE, metadata.eventType());
        add(headers, SCHEMA_VERSION, Integer.toString(metadata.schemaVersion()));
        add(headers, LEGACY_EVENT_VERSION, Integer.toString(metadata.schemaVersion()));
        add(headers, OCCURRED_AT, metadata.occurredAt().toString());
        add(headers, PRODUCER, metadata.producer());
        add(headers, AGGREGATE_TYPE, metadata.aggregateType());
        add(headers, AGGREGATE_ID, metadata.aggregateId());
        add(headers, CorrelationIds.KAFKA_HEADER, metadata.correlationId());
        if (metadata.traceparent() != null) {
            add(headers, CorrelationIds.TRACEPARENT_HEADER, metadata.traceparent());
        }
    }

    public static String value(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        return header == null || header.value() == null
                ? null
                : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static void add(Headers headers, String name, String value) {
        headers.remove(name);
        headers.add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
