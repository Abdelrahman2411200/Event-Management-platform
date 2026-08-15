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
}
