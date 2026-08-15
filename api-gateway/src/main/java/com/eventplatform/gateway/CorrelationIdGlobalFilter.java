package com.eventplatform.gateway;

import com.eventplatform.contracts.CorrelationIds;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = CorrelationIds.resolve(
                exchange.getRequest().getHeaders().getFirst(CorrelationIds.HTTP_HEADER));
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> headers.set(CorrelationIds.HTTP_HEADER, correlationId))
                        .build())
                .build();
        mutatedExchange.getAttributes().put(CorrelationIds.REQUEST_ATTRIBUTE, correlationId);
        mutatedExchange.getResponse().getHeaders().set(CorrelationIds.HTTP_HEADER, correlationId);
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
