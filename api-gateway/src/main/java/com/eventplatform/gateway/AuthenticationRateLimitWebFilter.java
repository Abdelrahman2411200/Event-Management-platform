package com.eventplatform.gateway;

import java.net.InetSocketAddress;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationRateLimitWebFilter implements WebFilter, Ordered {

    private static final Map<String, AuthenticationRateLimiter.Policy> POLICIES = Map.of(
            "/api/v1/auth/register", AuthenticationRateLimiter.Policy.REGISTRATION,
            "/api/v1/auth/login", AuthenticationRateLimiter.Policy.LOGIN,
            "/api/v1/auth/refresh", AuthenticationRateLimiter.Policy.REFRESH);

    private final AuthenticationRateLimiter rateLimiter;
    private final GatewaySecurityProperties properties;
    private final GatewayApiErrorWriter errorWriter;

    public AuthenticationRateLimitWebFilter(
            AuthenticationRateLimiter rateLimiter,
            GatewaySecurityProperties properties,
            GatewayApiErrorWriter errorWriter) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.getRateLimit().isEnabled()
                || exchange.getRequest().getMethod() != HttpMethod.POST) {
            return chain.filter(exchange);
        }
        AuthenticationRateLimiter.Policy policy = POLICIES.get(exchange.getRequest().getPath().value());
        if (policy == null) {
            return chain.filter(exchange);
        }
        return rateLimiter.check(policy, clientKey(exchange))
                .onErrorResume(exception -> errorWriter.write(
                        exchange,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "RATE_LIMIT_UNAVAILABLE",
                        "Authentication rate limiting is temporarily unavailable").then(Mono.empty()))
                .flatMap(decision -> {
                    decision.headers().forEach((name, value) -> exchange.getResponse().getHeaders().set(name, value));
                    if (decision.allowed()) {
                        return chain.filter(exchange);
                    }
                    return errorWriter.write(
                            exchange,
                            HttpStatus.TOO_MANY_REQUESTS,
                            "RATE_LIMIT_EXCEEDED",
                            "Too many authentication attempts; retry later");
                });
    }

    private String clientKey(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress == null || remoteAddress.getAddress() == null
                ? "unknown"
                : remoteAddress.getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
