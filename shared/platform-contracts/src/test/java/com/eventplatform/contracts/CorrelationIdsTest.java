package com.eventplatform.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorrelationIdsTest {

    @Test
    void preservesSafeIncomingValue() {
        assertThat(CorrelationIds.resolve("request-123")).isEqualTo("request-123");
    }

    @Test
    void replacesUnsafeIncomingValue() {
        assertThat(CorrelationIds.resolve("unsafe value\n")).isNotEqualTo("unsafe value\n");
    }

    @Test
    void replacesBlankIncomingValue() {
        assertThat(CorrelationIds.resolve("   ")).matches("[0-9a-f-]{36}");
    }

    @Test
    void acceptsTheMaximumSafeLength() {
        String candidate = "a".repeat(128);

        assertThat(CorrelationIds.resolve(candidate)).isEqualTo(candidate);
    }

    @Test
    void replacesValuesLongerThanTheMaximum() {
        String candidate = "a".repeat(129);

        assertThat(CorrelationIds.resolve(candidate)).isNotEqualTo(candidate);
    }
}
