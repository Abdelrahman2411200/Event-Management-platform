package com.eventplatform.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

class KafkaEventMetadataTest {

    @Test
    void writesCanonicalMetadataAndReadsItBack() {
        KafkaEventMetadata expected = new KafkaEventMetadata(
                UUID.randomUUID(),
                "event-platform.booking.confirmed.v1",
                1,
                Instant.parse("2026-08-16T10:00:00Z"),
                "correlation-1",
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01",
                "attendee-service",
                "Booking",
                UUID.randomUUID().toString());
        RecordHeaders headers = new RecordHeaders();

        KafkaEventHeaders.write(headers, expected);

        assertThat(KafkaEventMetadata.from(headers)).isEqualTo(expected);
        assertThat(KafkaEventHeaders.value(headers, KafkaEventHeaders.LEGACY_EVENT_ID))
                .isEqualTo(expected.messageId().toString());
    }

    @Test
    void readsPhaseFiveAliasesDuringARollingUpgrade() {
        KafkaEventMetadata expected = new KafkaEventMetadata(
                UUID.randomUUID(),
                "event-platform.payment.succeeded.v1",
                1,
                Instant.parse("2026-08-16T10:00:00Z"),
                "correlation-2",
                null,
                "payment-service",
                "Payment",
                UUID.randomUUID().toString());
        RecordHeaders headers = new RecordHeaders();
        KafkaEventHeaders.write(headers, expected);
        headers.remove(KafkaEventHeaders.MESSAGE_ID);
        headers.remove(KafkaEventHeaders.SCHEMA_VERSION);

        assertThat(KafkaEventMetadata.from(headers)).isEqualTo(expected);
    }

    @Test
    void rejectsUnversionedEventTypes() {
        assertThatThrownBy(() -> new KafkaEventMetadata(
                UUID.randomUUID(), "booking-confirmed", 1, Instant.now(),
                "correlation-3", null, "attendee-service", "Booking", UUID.randomUUID().toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("versioned platform convention");
    }
}
