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
                .paths(edgePaths())
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    private Paths edgePaths() {
        Paths paths = new Paths()
                .addPathItem("/api/v1/auth/register", post("Register an attendee account", "201", false))
                .addPathItem("/api/v1/auth/login", post("Authenticate with email and password", "200", false))
                .addPathItem("/api/v1/auth/refresh", post("Rotate a refresh token", "200", false))
                .addPathItem("/api/v1/auth/.well-known/jwks.json", get("Return the active public signing key", false))
                .addPathItem("/api/v1/auth/logout", post("Revoke the current refresh session", "200", true))
                .addPathItem("/api/v1/auth/sessions/revoke-all", post("Revoke every refresh session", "200", true))
                .addPathItem("/api/v1/auth/me", get("Return the authenticated account", true))
                .addPathItem("/api/v1/auth/users/{userId}/roles", put("Replace user roles (ADMIN)", true))
                .addPathItem("/api/v1/auth/audit-events", get("Return recent security audit events (ADMIN)", true));
        paths.addPathItem("/api/v1/events", new PathItem()
                .get(operation("Discover published events", "200", false, "Events"))
                .post(operation("Create a draft event", "201", true, "Events")));
        paths.addPathItem("/api/v1/events/{eventId}", new PathItem()
                .get(pathParameters(operation("Read a published event", "200", false, "Events"), "eventId"))
                .put(pathParameters(operation("Update an owned draft event", "200", true, "Events"), "eventId"))
                .delete(pathParameters(operation("Archive an owned event", "204", true, "Events"), "eventId")));
        paths.addPathItem("/api/v1/events/{eventId}/management", new PathItem()
                .get(pathParameters(operation("Read owned event management details", "200", true, "Events"), "eventId")));
        paths.addPathItem("/api/v1/events/{eventId}/transitions", new PathItem()
                .post(pathParameters(operation("Transition event lifecycle state", "200", true, "Events"), "eventId")));
        paths.addPathItem("/api/v1/events/{eventId}/ticket-types", new PathItem()
                .post(pathParameters(operation("Create a ticket product", "201", true, "Ticket inventory"), "eventId")));
        paths.addPathItem("/api/v1/events/{eventId}/ticket-types/{ticketTypeId}", new PathItem()
                .put(pathParameters(
                        operation("Update a ticket product", "200", true, "Ticket inventory"),
                        "eventId", "ticketTypeId"))
                .delete(pathParameters(
                        operation("Archive a ticket product", "204", true, "Ticket inventory"),
                        "eventId", "ticketTypeId")));
        paths.addPathItem("/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/inventory", new PathItem()
                .get(pathParameters(
                        operation("Check ticket inventory", "200", true, "Ticket inventory"),
                        "eventId", "ticketTypeId")));
        paths.addPathItem("/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/inventory/reservations", new PathItem()
                .post(idempotent(pathParameters(
                        operation("Create an expiring inventory hold", "201", true, "Ticket inventory"),
                        "eventId", "ticketTypeId"))));
        paths.addPathItem(
                "/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/inventory/reservations/{reservationId}/release",
                new PathItem().post(idempotent(pathParameters(
                        operation("Release an inventory hold", "200", true, "Ticket inventory"),
                        "eventId", "ticketTypeId", "reservationId"))));
        paths.addPathItem("/api/v1/event-categories", new PathItem()
                .get(operation("List public event categories", "200", false, "Events"))
                .post(operation("Create an event category (ADMIN)", "201", true, "Events")));
        paths.addPathItem("/api/v1/event-categories/{categoryId}", new PathItem()
                .put(pathParameters(operation("Update an event category (ADMIN)", "200", true, "Events"), "categoryId"))
                .delete(pathParameters(operation("Archive an event category (ADMIN)", "204", true, "Events"), "categoryId")));
        paths.addPathItem("/api/v1/venues", new PathItem()
                .post(operation("Create a venue", "201", true, "Venues")));
        paths.addPathItem("/api/v1/venues/{venueId}", new PathItem()
                .get(pathParameters(operation("Read a venue", "200", true, "Venues"), "venueId"))
                .put(pathParameters(operation("Update an owned venue", "200", true, "Venues"), "venueId"))
                .delete(pathParameters(operation("Archive an owned venue", "204", true, "Venues"), "venueId")));
        paths.addPathItem("/api/v1/venues/{venueId}/spaces", new PathItem()
                .post(pathParameters(operation("Create a venue room", "201", true, "Venues"), "venueId")));
        paths.addPathItem("/api/v1/venues/{venueId}/spaces/{spaceId}", new PathItem()
                .put(pathParameters(operation("Update a venue room", "200", true, "Venues"), "venueId", "spaceId"))
                .delete(pathParameters(operation("Archive a venue room", "204", true, "Venues"), "venueId", "spaceId")));
        paths.addPathItem("/api/v1/venues/{venueId}/availability/check", new PathItem()
                .post(pathParameters(operation("Check venue capacity and availability", "200", true, "Venues"), "venueId")));
        paths.addPathItem("/api/v1/venues/{venueId}/availability-blocks", new PathItem()
                .post(pathParameters(operation("Create a venue availability block", "201", true, "Venues"), "venueId")));
        paths.addPathItem("/api/v1/venues/{venueId}/availability-blocks/{blockId}", new PathItem()
                .delete(pathParameters(
                        operation("Release a venue availability block", "200", true, "Venues"),
                        "venueId", "blockId")));
        paths.addPathItem("/api/v1/venues/{venueId}/reservations", new PathItem()
                .post(pathParameters(operation("Reserve an event venue assignment", "201", true, "Venues"), "venueId")));
        paths.addPathItem("/api/v1/venues/{venueId}/reservations/{reservationId}", new PathItem()
                .delete(pathParameters(
                        operation("Release an event venue assignment", "200", true, "Venues"),
                        "venueId", "reservationId")));
        return paths;
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
        return operation(summary, successStatus, protectedRoute, "Authentication");
    }

    private Operation operation(
            String summary,
            String successStatus,
            boolean protectedRoute,
            String tag) {
        Operation operation = new Operation()
                .summary(summary)
                .addTagsItem(tag)
                .responses(new ApiResponses().addApiResponse(
                        successStatus,
                        new ApiResponse().description("Successful response from the downstream service")));
        if (protectedRoute) {
            operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
        }
        return operation;
    }

    private Operation pathParameters(Operation operation, String... names) {
        for (String name : names) {
            operation.addParametersItem(new Parameter()
                    .name(name)
                    .in("path")
                    .required(true)
                    .schema(new StringSchema().format("uuid")));
        }
        return operation;
    }

    private Operation idempotent(Operation operation) {
        return operation.addParametersItem(new Parameter()
                .name("Idempotency-Key")
                .in("header")
                .required(true)
                .schema(new StringSchema()
                        .minLength(1)
                        .maxLength(128)
                        .pattern("[A-Za-z0-9._:-]{1,128}")));
    }
}
