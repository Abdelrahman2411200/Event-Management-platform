package com.eventplatform.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);
    private final GatewayApiErrorWriter errorWriter;

    public GatewayErrorWebExceptionHandler(GatewayApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exception instanceof ResponseStatusException statusException
                && statusException.getStatusCode() instanceof HttpStatus status) {
            String message = statusException.getReason() == null
                    ? status.getReasonPhrase()
                    : statusException.getReason();
            return errorWriter.write(exchange, status, "GATEWAY_" + status.value(), message);
        }
        LOGGER.error("Unhandled gateway failure", exception);
        return errorWriter.write(
                exchange,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred");
    }
}
