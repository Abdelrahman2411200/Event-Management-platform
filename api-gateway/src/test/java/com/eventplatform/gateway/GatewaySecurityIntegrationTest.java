package com.eventplatform.gateway;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@AutoConfigureWebTestClient
@SpringBootTest(properties = {
    "spring.cloud.gateway.server.webflux.enabled=false",
    "platform.gateway.security.rate-limit.enabled=false",
    "management.health.redis.enabled=false",
    "management.endpoint.health.group.readiness.include=readinessState",
    "management.tracing.enabled=false"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @Autowired
    private GatewaySecurityProperties securityProperties;

    @Test
    void authenticationEndpointsArePublicButFutureDomainRoutesAreProtected() {
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"person@example.com\",\"password\":\"unused\"}")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));

        webTestClient.get()
                .uri("/api/v1/events")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));

        webTestClient.get()
                .uri("/api/v1/events/00000000-0000-0000-0000-000000000000")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));

        webTestClient.get()
                .uri("/api/v1/event-categories")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));

        webTestClient.get()
                .uri("/api/v1/attendees/example")
                .header("X-Correlation-Id", "protected-route")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Correlation-Id", "protected-route")
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");

        webTestClient.get()
                .uri("/api/v1/events/00000000-0000-0000-0000-000000000000/ticket-types/example/inventory")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void aValidatedBearerTokenPassesTheGatewaySecurityBoundary() {
        when(jwtDecoder.decode("valid-token")).thenReturn(Mono.just(validJwt("valid-token")));

        webTestClient.get()
                .uri("/api/v1/attendees/example")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
    }

    @Test
    void invalidBearerTokenUsesTheStandardGatewayError() {
        when(jwtDecoder.decode("invalid-token")).thenReturn(Mono.error(
                new JwtValidationException("invalid", List.of(new OAuth2Error("invalid_token")))));

        webTestClient.get()
                .uri("/api/v1/attendees/example")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .header("X-Correlation-Id", "invalid-gateway-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectHeader().valueEquals("X-Correlation-Id", "invalid-gateway-token")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.code").isEqualTo("INVALID_ACCESS_TOKEN")
                .jsonPath("$.correlationId").isEqualTo("invalid-gateway-token");
    }

    @Test
    void explicitCorsAndSecurityHeadersAreApplied() {
        assertThat(securityProperties.getAllowedOrigins()).containsExactly("http://localhost:3000");
        webTestClient.options()
                .uri(java.net.URI.create("http://gateway.local:8080/api/v1/auth/login"))
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
                .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);

        webTestClient.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer")
                .expectHeader().valueEquals("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY");
    }

    @Test
    void aDisallowedCorsOriginUsesTheStandardErrorContract() {
        webTestClient.options()
                .uri(java.net.URI.create("http://gateway.local:8080/api/v1/auth/login"))
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header("X-Correlation-Id", "cors-denied")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().valueEquals("X-Correlation-Id", "cors-denied")
                .expectBody()
                .jsonPath("$.code").isEqualTo("CORS_ORIGIN_DENIED")
                .jsonPath("$.correlationId").isEqualTo("cors-denied");
    }

    private Jwt validJwt(String tokenValue) {
        Instant now = Instant.now();
        return new Jwt(
                tokenValue,
                now,
                now.plusSeconds(600),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", UUID.randomUUID().toString(),
                        "iss", "urn:event-platform:auth",
                        "aud", List.of("event-platform-api"),
                        "sid", UUID.randomUUID().toString(),
                        "roles", List.of("ATTENDEE")));
    }
}
