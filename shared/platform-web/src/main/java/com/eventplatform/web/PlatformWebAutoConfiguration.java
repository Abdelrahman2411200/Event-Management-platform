package com.eventplatform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@AutoConfiguration
@EnableConfigurationProperties(PlatformMetadataProperties.class)
public class PlatformWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    ApiErrorResponseWriter apiErrorResponseWriter(ObjectMapper objectMapper) {
        return new ApiErrorResponseWriter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    GlobalApiExceptionHandler globalApiExceptionHandler() {
        return new GlobalApiExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain platformSecurityFilterChain(
            HttpSecurity http,
            ApiErrorResponseWriter errorWriter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errorWriter.write(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED.value(),
                                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                                "AUTHENTICATION_REQUIRED",
                                "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> errorWriter.write(
                                request,
                                response,
                                HttpStatus.FORBIDDEN.value(),
                                HttpStatus.FORBIDDEN.getReasonPhrase(),
                                "ACCESS_DENIED",
                                "Access is denied")))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    OpenAPI platformOpenApi(
            @Value("${spring.application.name:service}") String applicationName,
            PlatformMetadataProperties metadata) {
        return new OpenAPI().info(new Info()
                .title(applicationName)
                .description(metadata.getDescription())
                .version(metadata.getApiVersion()));
    }

    @Bean
    @ConditionalOnMissingBean(name = "platformInfoContributor")
    InfoContributor platformInfoContributor(
            @Value("${spring.application.name:service}") String applicationName,
            PlatformMetadataProperties metadata) {
        return builder -> builder.withDetail("app", Map.of(
                "name", applicationName,
                "description", metadata.getDescription(),
                "version", metadata.getVersion(),
                "apiVersion", metadata.getApiVersion()));
    }
}
