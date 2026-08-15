package com.eventplatform.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class AuthenticationRateLimitWebFilterTest {

    @Test
    void deniedLoginAttemptUsesTheStandardRateLimitContract() throws Exception {
        AuthenticationRateLimiter limiter = (policy, key) -> Mono.just(
                new AuthenticationRateLimiter.Decision(false, Map.of("X-RateLimit-Remaining", "0")));
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        AuthenticationRateLimitWebFilter filter = new AuthenticationRateLimitWebFilter(
                limiter, properties, new GatewayApiErrorWriter(new ObjectMapper().findAndRegisterModules()));
        MockServerWebExchange exchange = exchange("/api/v1/auth/login");
        AtomicBoolean downstreamCalled = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            downstreamCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(downstreamCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":\"RATE_LIMIT_EXCEEDED\"")
                .contains("\"status\":429");
    }

    @Test
    void allowedAttemptContinuesAndLimiterFailureFailsClosed() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        AtomicBoolean downstreamCalled = new AtomicBoolean();
        AuthenticationRateLimitWebFilter allowedFilter = new AuthenticationRateLimitWebFilter(
                (policy, key) -> Mono.just(new AuthenticationRateLimiter.Decision(true, Map.of())),
                properties,
                new GatewayApiErrorWriter(new ObjectMapper().findAndRegisterModules()));
        allowedFilter.filter(exchange("/api/v1/auth/register"), ignored -> {
            downstreamCalled.set(true);
            return Mono.empty();
        }).block();
        assertThat(downstreamCalled).isTrue();

        MockServerWebExchange unavailableExchange = exchange("/api/v1/auth/refresh");
        AuthenticationRateLimitWebFilter unavailableFilter = new AuthenticationRateLimitWebFilter(
                (policy, key) -> Mono.error(new IllegalStateException("redis unavailable")),
                properties,
                new GatewayApiErrorWriter(new ObjectMapper().findAndRegisterModules()));
        unavailableFilter.filter(unavailableExchange, ignored -> Mono.empty()).block();
        assertThat(unavailableExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(unavailableExchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"RATE_LIMIT_UNAVAILABLE\"");
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.POST, path)
                .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 12345))
                .build());
    }
}
