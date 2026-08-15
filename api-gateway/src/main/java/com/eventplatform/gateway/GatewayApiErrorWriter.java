package com.eventplatform.gateway;

import com.eventplatform.contracts.ApiError;
import com.eventplatform.contracts.CorrelationIds;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayApiErrorWriter {

    private final ObjectMapper objectMapper;

    public GatewayApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        String correlationId = correlationId(exchange);
        ApiError apiError = ApiError.of(
                status.value(), status.getReasonPhrase(), code, message,
                exchange.getRequest().getPath().value(), correlationId);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(apiError);
        } catch (JsonProcessingException exception) {
            body = "{\"code\":\"ERROR_SERIALIZATION_FAILED\"}".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(CorrelationIds.HTTP_HEADER, correlationId);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String correlationId(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE);
        return value instanceof String correlationId
                ? correlationId
                : CorrelationIds.resolve(exchange.getRequest().getHeaders().getFirst(CorrelationIds.HTTP_HEADER));
    }
}
