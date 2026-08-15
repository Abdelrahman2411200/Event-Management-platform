package com.eventplatform.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class UntrustedIdentityHeadersFilterTest {

    @Test
    void stripsClientIdentityHeadersButPreservesBearerAndCorrelationMetadata() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer original-token")
                .header("X-Correlation-Id", "correlation-123")
                .header("X-User-Id", "spoofed-user")
                .header("X-Authenticated-User-Roles", "ADMIN")
                .build());
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();

        new UntrustedIdentityHeadersFilter().filter(exchange, sanitized -> {
            forwarded.set(sanitized.getRequest().getHeaders());
            return reactor.core.publisher.Mono.empty();
        }).block();

        assertThat(forwarded.get().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer original-token");
        assertThat(forwarded.get().getFirst("X-Correlation-Id")).isEqualTo("correlation-123");
        assertThat(forwarded.get().getFirst("X-User-Id")).isNull();
        assertThat(forwarded.get().getFirst("X-Authenticated-User-Roles")).isNull();
    }
}
