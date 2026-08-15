package com.eventplatform.gateway;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI gatewayOpenApi(@Value("${platform.metadata.api-version:v1}") String apiVersion) {
        return new OpenAPI().info(new Info()
                .title("api-gateway")
                .description("Public routing boundary for the Event Management Platform")
                .version(apiVersion));
    }
}
