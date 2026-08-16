package com.eventplatform.attendee.api;

import com.eventplatform.attendee.application.TicketScanService;
import com.eventplatform.attendee.domain.ScanOutcome;
import com.eventplatform.attendee.security.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('EVENT_STAFF','ORGANIZER','ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class TicketScanController {
    private static final String IDEMPOTENCY_PATTERN = "[A-Za-z0-9._:-]{1,128}";
    private final TicketScanService scanService;

    public TicketScanController(TicketScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping("/tickets/validate")
    @Operation(summary = "Validate a signed ticket QR token without changing check-in state")
    public ResponseEntity<AttendeeApi.ScanResponse> validate(
            @Valid @RequestBody AttendeeApi.ScanRequest request,
            @Pattern(regexp = IDEMPOTENCY_PATTERN) @RequestHeader("Idempotency-Key") String idempotencyKey,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest) {
        AttendeeApi.ScanResponse response = scanService.validate(
                request, idempotencyKey, AuthenticatedActor.from(authentication), RequestContext.from(servletRequest));
        return ResponseEntity.status(status(response, false)).body(response);
    }

    @PostMapping("/check-ins")
    @Operation(summary = "Idempotently check in a valid issued ticket exactly once")
    public ResponseEntity<AttendeeApi.ScanResponse> checkIn(
            @Valid @RequestBody AttendeeApi.ScanRequest request,
            @Pattern(regexp = IDEMPOTENCY_PATTERN) @RequestHeader("Idempotency-Key") String idempotencyKey,
            JwtAuthenticationToken authentication,
            HttpServletRequest servletRequest) {
        AttendeeApi.ScanResponse response = scanService.checkIn(
                request, idempotencyKey, AuthenticatedActor.from(authentication), RequestContext.from(servletRequest));
        return ResponseEntity.status(status(response, true)).body(response);
    }

    private HttpStatus status(AttendeeApi.ScanResponse response, boolean checkIn) {
        if (response.outcome() == ScanOutcome.ORGANIZER_NOT_OWNER) return HttpStatus.FORBIDDEN;
        if (!response.accepted()) return HttpStatus.UNPROCESSABLE_ENTITY;
        if (checkIn && response.outcome() == ScanOutcome.CHECKED_IN) return HttpStatus.CREATED;
        return HttpStatus.OK;
    }
}
