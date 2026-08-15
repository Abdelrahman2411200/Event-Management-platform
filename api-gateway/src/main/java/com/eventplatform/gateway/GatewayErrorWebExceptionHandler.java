package com.eventplatform.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
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
        if (exception instanceof AuthenticationServiceException) {
            LOGGER.warn("JWT validation infrastructure is unavailable");
            return errorWriter.write(
                    exchange,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "TOKEN_VALIDATION_UNAVAILABLE",
                    "Token validation is temporarily unavailable");
        }
        if (exception instanceof ResponseStatusException statusException
                && statusException.getStatusCode() instanceof HttpStatus status) {
            return errorWriter.write(exchange, status, "GATEWAY_" + status.value(), safeMessage(status));
        }
        LOGGER.error("Unhandled gateway failure", exception);
        return errorWriter.write(
                exchange,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    private String safeMessage(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "The requested gateway route was not found";
            case SERVICE_UNAVAILABLE -> "A downstream service is temporarily unavailable";
            case GATEWAY_TIMEOUT -> "A downstream service timed out";
            default -> "The gateway could not complete the request";
        };
    }
}
