package com.eventplatform.gateway;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI gatewayOpenApi(@Value("${platform.metadata.api-version:v1}") String apiVersion) {
        return new OpenAPI()
                .info(new Info()
                        .title("api-gateway")
                        .description("JWT-validating routing and abuse-control boundary for the Event Management Platform")
                        .version(apiVersion))
                .paths(authenticationPaths())
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    private Paths authenticationPaths() {
        return new Paths()
                .addPathItem("/api/v1/auth/register", post("Register an attendee account", "201", false))
                .addPathItem("/api/v1/auth/login", post("Authenticate with email and password", "200", false))
                .addPathItem("/api/v1/auth/refresh", post("Rotate a refresh token", "200", false))
                .addPathItem("/api/v1/auth/.well-known/jwks.json", get("Return the active public signing key", false))
                .addPathItem("/api/v1/auth/logout", post("Revoke the current refresh session", "200", true))
                .addPathItem("/api/v1/auth/sessions/revoke-all", post("Revoke every refresh session", "200", true))
                .addPathItem("/api/v1/auth/me", get("Return the authenticated account", true))
                .addPathItem("/api/v1/auth/users/{userId}/roles", put("Replace user roles (ADMIN)", true))
                .addPathItem("/api/v1/auth/audit-events", get("Return recent security audit events (ADMIN)", true));
    }

    private PathItem post(String summary, String successStatus, boolean protectedRoute) {
        return new PathItem().post(operation(summary, successStatus, protectedRoute));
    }

    private PathItem get(String summary, boolean protectedRoute) {
        return new PathItem().get(operation(summary, "200", protectedRoute));
    }

    private PathItem put(String summary, boolean protectedRoute) {
        return new PathItem().put(operation(summary, "200", protectedRoute)
                .addParametersItem(new Parameter()
                        .name("userId")
                        .in("path")
                        .required(true)
                        .schema(new StringSchema().format("uuid"))));
    }

    private Operation operation(String summary, String successStatus, boolean protectedRoute) {
        Operation operation = new Operation()
                .summary(summary)
                .addTagsItem("Authentication")
                .responses(new ApiResponses().addApiResponse(
                        successStatus,
                        new ApiResponse().description("Successful response from auth-service")));
        if (protectedRoute) {
            operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
        }
        return operation;
    }
}
