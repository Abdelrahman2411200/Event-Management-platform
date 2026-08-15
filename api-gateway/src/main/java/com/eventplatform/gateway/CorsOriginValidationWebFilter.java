package com.eventplatform.gateway;

import java.util.HashSet;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class CorsOriginValidationWebFilter implements WebFilter, Ordered {

    private final Set<String> allowedOrigins;
    private final GatewayApiErrorWriter errorWriter;

    public CorsOriginValidationWebFilter(
            GatewaySecurityProperties properties,
            GatewayApiErrorWriter errorWriter) {
        this.allowedOrigins = new HashSet<>(properties.getAllowedOrigins());
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String origin = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (origin != null && !allowedOrigins.contains(origin)) {
            return errorWriter.write(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "CORS_ORIGIN_DENIED",
                    "The request origin is not allowed");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 15;
    }
}
