package com.eventplatform.event.api;

import com.eventplatform.event.application.EventManagementService;
import com.eventplatform.event.application.PublicEventQueryService;
import com.eventplatform.event.domain.EventStatus;
import com.eventplatform.event.security.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventManagementService eventService;
    private final PublicEventQueryService publicQueryService;

    public EventController(
            EventManagementService eventService,
            PublicEventQueryService publicQueryService) {
        this.eventService = eventService;
        this.publicQueryService = publicQueryService;
    }

    @GetMapping
    @Operation(summary = "Discover published events with pagination and filters")
    public EventApi.PageResponse<EventApi.PublicEventSummary> list(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startsAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startsBefore,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return publicQueryService.list(categoryId, startsAfter, startsBefore, status, search, page, size);
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Read public-safe published event details")
    public EventApi.PublicEventDetail detail(@PathVariable UUID eventId) {
        return publicQueryService.detail(eventId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create an organizer-owned draft event")
    public ResponseEntity<EventApi.EventResponse> create(
            @Valid @RequestBody EventApi.EventRequest request,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest) {
        EventApi.EventResponse response = eventService.create(
                request, actor(authentication), RequestContext.from(servletRequest));
        return ResponseEntity.created(URI.create("/api/v1/events/" + response.id() + "/management"))
                .body(response);
    }

    @GetMapping("/{eventId}/management")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Read full event management details")
    public EventApi.EventResponse management(
            @PathVariable UUID eventId,
            JwtAuthenticationToken authentication) {
        return eventService.getManagement(eventId, actor(authentication));
    }

    @PutMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update an owned draft event")
    public EventApi.EventResponse update(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventApi.EventRequest request,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest) {
        return eventService.update(
                eventId, request, actor(authentication), RequestContext.from(servletRequest));
    }

    @PostMapping("/{eventId}/transitions")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Apply a validated event lifecycle transition")
    public EventApi.EventResponse transition(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventApi.EventTransitionRequest request,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest) {
        return eventService.transition(
                eventId,
                request.targetStatus(),
                actor(authentication),
                RequestContext.from(servletRequest),
                servletRequest.getHeader(HttpHeaders.AUTHORIZATION));
    }

    @DeleteMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Archive an owned draft, cancelled, or completed event")
    public ResponseEntity<Void> archive(
            @PathVariable UUID eventId,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest) {
        eventService.archive(eventId, actor(authentication), RequestContext.from(servletRequest));
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedActor actor(JwtAuthenticationToken authentication) {
        return AuthenticatedActor.from(authentication);
    }
}
