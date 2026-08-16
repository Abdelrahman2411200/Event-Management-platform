package com.eventplatform.attendee.api;

import com.eventplatform.attendee.application.BookingCoordinator;
import com.eventplatform.attendee.application.BookingQueryService;
import com.eventplatform.attendee.security.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ATTENDEE')")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {
    private static final String IDEMPOTENCY_PATTERN = "[A-Za-z0-9._:-]{1,128}";
    private final BookingCoordinator coordinator;
    private final BookingQueryService queryService;

    public BookingController(BookingCoordinator coordinator, BookingQueryService queryService) {
        this.coordinator = coordinator;
        this.queryService = queryService;
    }

    @PostMapping("/bookings")
    @Operation(summary = "Create an idempotent booking and authoritative event inventory hold")
    public ResponseEntity<AttendeeApi.BookingResponse> create(
            @Valid @RequestBody AttendeeApi.CreateBookingRequest request,
            @Pattern(regexp = IDEMPOTENCY_PATTERN) @RequestHeader("Idempotency-Key") String idempotencyKey,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest) {
        AttendeeApi.BookingResponse response = coordinator.create(
                request, idempotencyKey, AuthenticatedActor.from(authentication), RequestContext.from(servletRequest));
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + response.id())).body(response);
    }

    @GetMapping("/bookings")
    @Operation(summary = "Read booking history owned by the authenticated attendee")
    public List<AttendeeApi.BookingResponse> history(JwtAuthenticationToken authentication) {
        return queryService.history(AuthenticatedActor.from(authentication));
    }

    @GetMapping("/bookings/{bookingId}")
    @Operation(summary = "Read one booking with strict attendee ownership")
    public AttendeeApi.BookingResponse get(
            @PathVariable UUID bookingId, JwtAuthenticationToken authentication) {
        return queryService.get(bookingId, AuthenticatedActor.from(authentication));
    }

    @GetMapping("/attendees/me/tickets")
    @Operation(summary = "Read the authenticated attendee's current or complete ticket list")
    public AttendeeApi.TicketListResponse tickets(
            @RequestParam(defaultValue = "true") boolean upcomingOnly,
            JwtAuthenticationToken authentication) {
        return queryService.tickets(AuthenticatedActor.from(authentication), upcomingOnly);
    }
}
