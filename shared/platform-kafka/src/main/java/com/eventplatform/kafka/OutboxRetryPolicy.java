package com.eventplatform.kafka;

import java.time.Duration;
import java.time.Instant;

public final class OutboxRetryPolicy {
    private OutboxRetryPolicy() {
    }

    public static Instant nextAttempt(Instant now, int attempts) {
        long seconds = Math.min(300L, 1L << Math.min(Math.max(attempts, 1), 8));
        return now.plus(Duration.ofSeconds(seconds));
    }

    public static String safeError(String error) {
        String value = error == null || error.isBlank() ? "Kafka publication failed" : error;
        return value.substring(0, Math.min(value.length(), 500));
    }
}
