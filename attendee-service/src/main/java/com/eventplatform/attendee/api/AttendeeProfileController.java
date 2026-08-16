package com.eventplatform.attendee.api;

import com.eventplatform.attendee.application.AttendeeProfileService;
import com.eventplatform.attendee.security.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendees/me")
@PreAuthorize("hasRole('ATTENDEE')")
@SecurityRequirement(name = "bearerAuth")
public class AttendeeProfileController {
    private final AttendeeProfileService profileService;

    public AttendeeProfileController(AttendeeProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(summary = "Read the authenticated attendee profile")
    public AttendeeApi.ProfileResponse get(JwtAuthenticationToken authentication) {
        return profileService.get(AuthenticatedActor.from(authentication));
    }

    @PutMapping
    @Operation(summary = "Create or update the authenticated attendee profile")
    public AttendeeApi.ProfileResponse update(
            @Valid @RequestBody AttendeeApi.ProfileRequest request,
            JwtAuthenticationToken authentication) {
        return profileService.update(request, AuthenticatedActor.from(authentication));
    }
}
