package com.eventplatform.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureObservability
@AutoConfigureWebTestClient
@SpringBootTest(properties = {
    "management.health.redis.enabled=false",
    "management.endpoint.health.group.readiness.include=readinessState",
    "management.tracing.enabled=false"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void contextLoads() {
    }

    @Test
    void operationalEndpointsEchoCorrelationId() {
        webTestClient.get()
                .uri("/actuator/health/readiness")
                .header("X-Correlation-Id", "gateway-test-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "gateway-test-123")
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void prometheusMetricsAreAvailableForScraping() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("jvm_info"));
    }

    @Test
    void openApiDocumentsTheAuthenticationEdgeContract() {
        webTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$['paths']['/api/v1/auth/register']['post']").exists()
                .jsonPath("$['paths']['/api/v1/auth/logout']['post']['security'][0]['bearerAuth']").isArray()
                .jsonPath("$['paths']['/api/v1/events/{eventId}/transitions']['post']").exists()
                .jsonPath("$['paths']['/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/inventory/reservations']['post']['parameters'][2]['name']")
                .isEqualTo("Idempotency-Key")
                .jsonPath("$['paths']['/api/v1/venues/{venueId}/spaces/{spaceId}']['put']").exists()
                .jsonPath("$['paths']['/api/v1/venues/{venueId}/availability-blocks']['post']").exists()
                .jsonPath("$['components']['securitySchemes']['bearerAuth']['scheme']").isEqualTo("bearer");
    }

    @Test
    void deniedRequestsUseTheStandardErrorContract() {
        webTestClient.get()
                .uri("/not-allowed")
                .header("X-Correlation-Id", "gateway-denied-123")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Correlation-Id", "gateway-denied-123")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED")
                .jsonPath("$.message").isEqualTo("Authentication is required")
                .jsonPath("$.path").isEqualTo("/not-allowed")
                .jsonPath("$.correlationId").isEqualTo("gateway-denied-123")
                .jsonPath("$.validationDetails").isArray();
    }
}
