package com.eventplatform.venue.api;

import com.eventplatform.venue.application.VenueAvailabilityService;
import com.eventplatform.venue.application.VenueManagementService;
import com.eventplatform.venue.domain.AvailabilityKind;
import com.eventplatform.venue.security.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/venues")
@PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class VenueController {

    private final VenueManagementService venueService;
    private final VenueAvailabilityService availabilityService;

    public VenueController(
            VenueManagementService venueService,
            VenueAvailabilityService availabilityService) {
        this.venueService = venueService;
        this.availabilityService = availabilityService;
    }

    @PostMapping
    @Operation(summary = "Create an organizer-owned venue")
    public ResponseEntity<VenueApi.VenueResponse> create(
            @Valid @RequestBody VenueApi.CreateVenueRequest request,
            JwtAuthenticationToken authentication) {
        VenueApi.VenueResponse response = venueService.create(request, actor(authentication));
        return ResponseEntity.created(URI.create("/api/v1/venues/" + response.id())).body(response);
    }

    @GetMapping("/{venueId}")
    @Operation(summary = "Read a venue and its rooms")
    public VenueApi.VenueResponse get(
            @PathVariable UUID venueId,
            JwtAuthenticationToken authentication) {
        return venueService.get(venueId, actor(authentication));
    }

    @PutMapping("/{venueId}")
    @Operation(summary = "Update an owned venue")
    public VenueApi.VenueResponse update(
            @PathVariable UUID venueId,
            @Valid @RequestBody VenueApi.UpdateVenueRequest request,
            JwtAuthenticationToken authentication) {
        return venueService.update(venueId, request, actor(authentication));
    }

    @DeleteMapping("/{venueId}")
    @Operation(summary = "Archive an owned venue")
    public ResponseEntity<Void> archive(
            @PathVariable UUID venueId,
            JwtAuthenticationToken authentication) {
        venueService.archive(venueId, actor(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{venueId}/spaces")
    @Operation(summary = "Add an independently capacity-limited room")
    public ResponseEntity<VenueApi.VenueSpaceResponse> createSpace(
            @PathVariable UUID venueId,
            @Valid @RequestBody VenueApi.VenueSpaceRequest request,
            JwtAuthenticationToken authentication) {
        VenueApi.VenueSpaceResponse response = venueService.createSpace(venueId, request, actor(authentication));
        return ResponseEntity.created(URI.create(
                        "/api/v1/venues/" + venueId + "/spaces/" + response.id()))
                .body(response);
    }

    @PutMapping("/{venueId}/spaces/{spaceId}")
    @Operation(summary = "Update an owned venue room")
    public VenueApi.VenueSpaceResponse updateSpace(
            @PathVariable UUID venueId,
            @PathVariable UUID spaceId,
            @Valid @RequestBody VenueApi.VenueSpaceRequest request,
            JwtAuthenticationToken authentication) {
        return venueService.updateSpace(venueId, spaceId, request, actor(authentication));
    }

    @DeleteMapping("/{venueId}/spaces/{spaceId}")
    @Operation(summary = "Archive an owned venue room")
    public ResponseEntity<Void> archiveSpace(
            @PathVariable UUID venueId,
            @PathVariable UUID spaceId,
            JwtAuthenticationToken authentication) {
        venueService.archiveSpace(venueId, spaceId, actor(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{venueId}/availability/check")
    @Operation(summary = "Validate venue capacity and availability")
    public VenueApi.AvailabilityCheckResponse checkAvailability(
            @PathVariable UUID venueId,
            @Valid @RequestBody VenueApi.AvailabilityCheckRequest request) {
        return availabilityService.check(venueId, request);
    }

    @PostMapping("/{venueId}/availability-blocks")
    @Operation(summary = "Block a venue or room time window")
    public ResponseEntity<VenueApi.AvailabilityEntryResponse> createBlock(
            @PathVariable UUID venueId,
            @Valid @RequestBody VenueApi.AvailabilityBlockRequest request,
            JwtAuthenticationToken authentication) {
        VenueApi.AvailabilityEntryResponse response = availabilityService.createBlock(
                venueId, request, actor(authentication));
        return ResponseEntity.created(URI.create(
                        "/api/v1/venues/" + venueId + "/availability-blocks/" + response.id()))
                .body(response);
    }

    @DeleteMapping("/{venueId}/availability-blocks/{blockId}")
    @Operation(summary = "Release an owned availability block")
    public VenueApi.AvailabilityEntryResponse releaseBlock(
            @PathVariable UUID venueId,
            @PathVariable UUID blockId,
            JwtAuthenticationToken authentication) {
        return availabilityService.release(
                venueId, blockId, actor(authentication), AvailabilityKind.BLOCK);
    }

    @PostMapping("/{venueId}/reservations")
    @Operation(summary = "Idempotently reserve a venue assignment for an event")
    public ResponseEntity<VenueApi.AvailabilityEntryResponse> reserve(
            @PathVariable UUID venueId,
            @Valid @RequestBody VenueApi.AvailabilityReservationRequest request,
            JwtAuthenticationToken authentication) {
        VenueApi.AvailabilityEntryResponse response = availabilityService.reserve(
                venueId, request, actor(authentication));
        return ResponseEntity.created(URI.create(
                        "/api/v1/venues/" + venueId + "/reservations/" + response.id()))
                .body(response);
    }

    @DeleteMapping("/{venueId}/reservations/{reservationId}")
    @Operation(summary = "Release an event venue assignment")
    public VenueApi.AvailabilityEntryResponse releaseReservation(
            @PathVariable UUID venueId,
            @PathVariable UUID reservationId,
            JwtAuthenticationToken authentication) {
        return availabilityService.release(
                venueId, reservationId, actor(authentication), AvailabilityKind.EVENT_RESERVATION);
    }

    private AuthenticatedActor actor(JwtAuthenticationToken authentication) {
        return AuthenticatedActor.from(authentication);
    }
}
