package com.eventplatform.event.api;

import com.eventplatform.event.application.TicketTypeService;
import com.eventplatform.event.security.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventId}/ticket-types")
@PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    public TicketTypeController(TicketTypeService ticketTypeService) {
        this.ticketTypeService = ticketTypeService;
    }

    @PostMapping
    @Operation(summary = "Create an event-owned ticket product")
    public ResponseEntity<EventApi.TicketTypeResponse> create(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventApi.TicketTypeRequest request,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest) {
        EventApi.TicketTypeResponse response = ticketTypeService.create(
                eventId,
                request,
                AuthenticatedActor.from(authentication),
                RequestContext.from(servletRequest));
        return ResponseEntity.created(URI.create(
                        "/api/v1/events/" + eventId + "/ticket-types/" + response.id()))
                .body(response);
    }

    @PutMapping("/{ticketTypeId}")
    @Operation(summary = "Update an event-owned ticket product")
    public EventApi.TicketTypeResponse update(
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId,
            @Valid @RequestBody EventApi.TicketTypeRequest request,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest) {
        return ticketTypeService.update(
                eventId,
                ticketTypeId,
                request,
                AuthenticatedActor.from(authentication),
                RequestContext.from(servletRequest));
    }

    @DeleteMapping("/{ticketTypeId}")
    @Operation(summary = "Archive an event-owned ticket product")
    public ResponseEntity<Void> archive(
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest) {
        ticketTypeService.archive(
                eventId,
                ticketTypeId,
                AuthenticatedActor.from(authentication),
                RequestContext.from(servletRequest));
        return ResponseEntity.noContent().build();
    }
}
