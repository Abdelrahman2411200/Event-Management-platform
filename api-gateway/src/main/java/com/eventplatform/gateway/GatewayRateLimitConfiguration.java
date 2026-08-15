package com.eventplatform.gateway;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class GatewayRateLimitConfiguration {

    @Bean("registrationRedisRateLimiter")
    RedisRateLimiter registrationRedisRateLimiter(GatewaySecurityProperties properties) {
        GatewaySecurityProperties.Policy policy = properties.getRateLimit().getRegistration();
        return new RedisRateLimiter(policy.getReplenishRate(), policy.getBurstCapacity(), 1);
    }

    @Bean("loginRedisRateLimiter")
    @Primary
    RedisRateLimiter loginRedisRateLimiter(GatewaySecurityProperties properties) {
        GatewaySecurityProperties.Policy policy = properties.getRateLimit().getLogin();
        return new RedisRateLimiter(policy.getReplenishRate(), policy.getBurstCapacity(), 1);
    }

    @Bean("refreshRedisRateLimiter")
    RedisRateLimiter refreshRedisRateLimiter(GatewaySecurityProperties properties) {
        GatewaySecurityProperties.Policy policy = properties.getRateLimit().getRefresh();
        return new RedisRateLimiter(policy.getReplenishRate(), policy.getBurstCapacity(), 1);
    }

    @Bean
    AuthenticationRateLimiter authenticationRateLimiter(
            @Qualifier("registrationRedisRateLimiter") RedisRateLimiter registration,
            @Qualifier("loginRedisRateLimiter") RedisRateLimiter login,
            @Qualifier("refreshRedisRateLimiter") RedisRateLimiter refresh) {
        return (policy, clientKey) -> {
            RedisRateLimiter delegate = switch (policy) {
                case REGISTRATION -> registration;
                case LOGIN -> login;
                case REFRESH -> refresh;
            };
            return delegate.isAllowed("auth-" + policy.name().toLowerCase(), clientKey)
                    .map(response -> new AuthenticationRateLimiter.Decision(
                            response.isAllowed(), response.getHeaders()));
        };
    }
}
