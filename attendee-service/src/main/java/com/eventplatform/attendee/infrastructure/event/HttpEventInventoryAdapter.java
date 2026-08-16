package com.eventplatform.attendee.infrastructure.event;

import com.eventplatform.attendee.api.AttendeeApiException;
import com.eventplatform.attendee.api.RequestContext;
import com.eventplatform.attendee.application.EventInventoryPort;
import com.eventplatform.contracts.CorrelationIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpEventInventoryAdapter implements EventInventoryPort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpEventInventoryAdapter(RestClient eventInventoryRestClient, ObjectMapper objectMapper) {
        this.restClient = eventInventoryRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public InventoryHold reserve(
            UUID eventId, UUID ticketTypeId, int quantity, String idempotencyKey, RequestContext context) {
        return invoke(() -> restClient.post()
                .uri("/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/inventory/reservations", eventId, ticketTypeId)
                .headers(headers -> headers(headers, idempotencyKey, context))
                .body(new ReserveRequest(quantity))
                .retrieve()
                .body(InventoryHold.class));
    }

    @Override
    public InventoryHold confirm(
            UUID eventId, UUID ticketTypeId, UUID reservationId, String idempotencyKey, RequestContext context) {
        return invoke(() -> restClient.post()
                .uri("/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/inventory/reservations/{reservationId}/confirm",
                        eventId, ticketTypeId, reservationId)
                .headers(headers -> headers(headers, idempotencyKey, context))
                .retrieve()
                .body(InventoryHold.class));
    }

    @Override
    public InventoryHold release(
            UUID eventId, UUID ticketTypeId, UUID reservationId, String idempotencyKey, RequestContext context) {
        return invoke(() -> restClient.post()
                .uri("/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/inventory/reservations/{reservationId}/release",
                        eventId, ticketTypeId, reservationId)
                .headers(headers -> headers(headers, idempotencyKey, context))
                .retrieve()
                .body(InventoryHold.class));
    }

    private void headers(HttpHeaders headers, String idempotencyKey, RequestContext context) {
        if (context.bearerToken() == null || context.bearerToken().isBlank()) {
            throw new AttendeeApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "A bearer token is required");
        }
        headers.setBearerAuth(context.bearerToken().replaceFirst("(?i)^Bearer\\s+", ""));
        headers.set("Idempotency-Key", idempotencyKey);
        headers.set(CorrelationIds.HTTP_HEADER, context.correlationId());
        if (context.traceparent() != null) headers.set(CorrelationIds.TRACEPARENT_HEADER, context.traceparent());
    }

    private InventoryHold invoke(InventoryCall call) {
        try {
            InventoryHold response = call.execute();
            if (response == null) {
                throw unavailable();
            }
            return response;
        } catch (RestClientResponseException exception) {
            HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
            String code = "EVENT_INVENTORY_REJECTED";
            String message = "The event service rejected the inventory operation";
            try {
                var body = objectMapper.readTree(exception.getResponseBodyAsByteArray());
                if (body.hasNonNull("code")) code = body.get("code").asText();
                if (body.hasNonNull("message")) message = body.get("message").asText();
            } catch (Exception ignored) {
                // The stable local error remains safe when a downstream body is malformed.
            }
            throw new AttendeeApiException(status == null ? HttpStatus.BAD_GATEWAY : status, code, message);
        } catch (ResourceAccessException exception) {
            throw unavailable();
        }
    }

    private AttendeeApiException unavailable() {
        return new AttendeeApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "EVENT_INVENTORY_UNAVAILABLE",
                "Ticket inventory is temporarily unavailable; retry with the same Idempotency-Key");
    }

    private record ReserveRequest(int quantity) {
    }

    @FunctionalInterface
    private interface InventoryCall {
        InventoryHold execute();
    }
}
