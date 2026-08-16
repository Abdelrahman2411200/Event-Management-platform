package com.eventplatform.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {

    @Test
    void usesBoundedExponentialBackoff() {
        Instant now = Instant.parse("2026-08-16T10:00:00Z");

        assertThat(OutboxRetryPolicy.nextAttempt(now, 1)).isEqualTo(now.plusSeconds(2));
        assertThat(OutboxRetryPolicy.nextAttempt(now, 20)).isEqualTo(now.plusSeconds(256));
    }

    @Test
    void boundsPersistedErrors() {
        assertThat(OutboxRetryPolicy.safeError("x".repeat(600))).hasSize(500);
        assertThat(OutboxRetryPolicy.safeError(null)).isEqualTo("Kafka publication failed");
    }
}
