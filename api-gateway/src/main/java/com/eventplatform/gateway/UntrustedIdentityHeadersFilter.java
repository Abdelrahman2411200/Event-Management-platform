package com.eventplatform.gateway;

import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class UntrustedIdentityHeadersFilter implements WebFilter, Ordered {

    static final Set<String> UNTRUSTED_HEADERS = Set.of(
            "X-User-Id",
            "X-User-Email",
            "X-User-Roles",
            "X-Authenticated-User-Id",
            "X-Authenticated-User-Email",
            "X-Authenticated-User-Roles");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerWebExchange sanitized = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> UNTRUSTED_HEADERS.forEach(headers::remove))
                        .build())
                .build();
        return chain.filter(sanitized);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
