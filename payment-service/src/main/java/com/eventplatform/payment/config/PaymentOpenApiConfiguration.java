package com.eventplatform.payment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentOpenApiConfiguration {
    @Bean OpenAPI paymentOpenApi() {
        return new OpenAPI().info(new Info().title("Payment Service API").version("v1")
                .description("Provider-neutral payment, webhook, transaction history, refund, and reconciliation API. Mutating client requests require Idempotency-Key."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
