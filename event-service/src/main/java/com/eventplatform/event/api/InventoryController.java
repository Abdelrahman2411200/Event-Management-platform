package com.eventplatform.event.api;

import com.eventplatform.event.application.InventoryService;
import com.eventplatform.event.security.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/inventory")
@PreAuthorize("hasAnyRole('ATTENDEE','ORGANIZER','ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private static final String IDEMPOTENCY_PATTERN = "[A-Za-z0-9._:-]{1,128}";

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @Operation(summary = "Check current ticket inventory availability")
    public EventApi.InventoryAvailabilityResponse availability(
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId) {
        return inventoryService.availability(eventId, ticketTypeId);
    }

    @PostMapping("/reservations")
    @Operation(summary = "Idempotently hold ticket inventory without creating issued tickets")
    public ResponseEntity<EventApi.InventoryReservationResponse> reserve(
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId,
            @Pattern(regexp = IDEMPOTENCY_PATTERN) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EventApi.ReserveInventoryRequest request,
            JwtAuthenticationToken authentication) {
        EventApi.InventoryReservationResponse response = inventoryService.reserve(
                eventId,
                ticketTypeId,
                request.quantity(),
                idempotencyKey,
                AuthenticatedActor.from(authentication));
        return ResponseEntity.created(URI.create(
                        "/api/v1/events/" + eventId + "/ticket-types/" + ticketTypeId
                                + "/inventory/reservations/" + response.id()))
                .body(response);
    }

    @PostMapping("/reservations/{reservationId}/release")
    @Operation(summary = "Idempotently release a ticket inventory hold")
    public EventApi.InventoryReservationResponse release(
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId,
            @PathVariable UUID reservationId,
            @Pattern(regexp = IDEMPOTENCY_PATTERN) @RequestHeader("Idempotency-Key") String idempotencyKey,
            JwtAuthenticationToken authentication) {
        return inventoryService.release(
                eventId,
                ticketTypeId,
                reservationId,
                idempotencyKey,
                AuthenticatedActor.from(authentication));
    }
}
