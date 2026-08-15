package com.eventplatform.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

class GatewayErrorWebExceptionHandlerTest {

    @Test
    void downstreamStatusErrorsDoNotExposeInternalReasons() {
        GatewayErrorWebExceptionHandler handler = new GatewayErrorWebExceptionHandler(
                new GatewayApiErrorWriter(new ObjectMapper().findAndRegisterModules()));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/events/private").build());

        handler.handle(
                exchange,
                new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "internal-service-name and credential-marker"))
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"GATEWAY_503\"")
                .contains("A downstream service is temporarily unavailable")
                .doesNotContain("internal-service-name")
                .doesNotContain("credential-marker");
    }
}
