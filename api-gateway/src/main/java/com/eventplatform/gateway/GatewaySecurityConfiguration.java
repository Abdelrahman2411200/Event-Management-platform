package com.eventplatform.gateway;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfiguration {

    @Bean
    SecurityWebFilterChain gatewaySecurityFilterChain(
            ServerHttpSecurity http,
            GatewayApiErrorWriter errorWriter,
            CorsConfigurationSource corsConfigurationSource,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> gatewayJwtAuthenticationConverter) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/.well-known/jwks.json",
                                "/oauth2/**",
                                "/login/oauth2/**")
                        .permitAll()
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/events",
                                "/api/v1/events/*",
                                "/api/v1/event-categories")
                        .permitAll()
                        .pathMatchers("/api/v1/**")
                        .authenticated()
                        .anyExchange()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(gatewayJwtAuthenticationConverter))
                        .authenticationEntryPoint((exchange, exception) -> {
                            exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
                            return errorWriter.write(
                                    exchange,
                                    HttpStatus.UNAUTHORIZED,
                                    "INVALID_ACCESS_TOKEN",
                                    "A valid access token is required");
                        }))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, exception) -> errorWriter.write(
                                exchange,
                                HttpStatus.UNAUTHORIZED,
                                "AUTHENTICATION_REQUIRED",
                                "Authentication is required"))
                        .accessDeniedHandler((exchange, exception) -> errorWriter.write(
                                exchange,
                                HttpStatus.FORBIDDEN,
                                "ACCESS_DENIED",
                                "Access is denied")))
                .build();
    }

    @Bean
    CorsConfigurationSource gatewayCorsConfigurationSource(GatewaySecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Correlation-Id", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of(
                "X-Correlation-Id", "X-RateLimit-Remaining", "X-RateLimit-Replenish-Rate", "X-RateLimit-Burst-Capacity"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
