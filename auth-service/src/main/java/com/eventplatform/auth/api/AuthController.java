package com.eventplatform.auth.api;

import com.eventplatform.auth.audit.SecurityAuditService;
import com.eventplatform.auth.user.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final SecurityAuditService auditService;

    public AuthController(AuthenticationService authenticationService, SecurityAuditService auditService) {
        this.authenticationService = authenticationService;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register an attendee account")
    ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest servletRequest) {
        UserResponse user = authenticationService.register(request, RequestMetadata.from(servletRequest));
        return ResponseEntity.created(URI.create("/api/v1/auth/users/" + user.id())).body(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password")
    TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authenticationService.login(request, RequestMetadata.from(servletRequest));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token and issue a new token pair")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        return authenticationService.refresh(request, RequestMetadata.from(servletRequest));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoke the current refresh session")
    RevocationResponse logout(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return authenticationService.revokeCurrent(
                userId(jwt), sessionId(jwt), RequestMetadata.from(servletRequest));
    }

    @PostMapping("/sessions/revoke-all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoke all refresh sessions for the current user")
    RevocationResponse revokeAll(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return authenticationService.revokeAll(userId(jwt), RequestMetadata.from(servletRequest));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Return the authenticated account")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authenticationService.currentUser(userId(jwt));
    }

    @PutMapping("/users/{userId}/roles")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Replace user roles (ADMIN only)")
    UserResponse replaceRoles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody RoleUpdateRequest request,
            HttpServletRequest servletRequest) {
        return authenticationService.replaceRoles(
                userId(jwt), userId, request.roles(), RequestMetadata.from(servletRequest));
    }

    @GetMapping("/audit-events")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Return recent security audit events (ADMIN only)")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    List<AuditEventResponse> auditEvents(@RequestParam(defaultValue = "100") int limit) {
        return auditService.recent(limit);
    }

    private UUID userId(Jwt jwt) {
        return parseUuid(jwt == null ? null : jwt.getSubject(), "sub");
    }

    private UUID sessionId(Jwt jwt) {
        return parseUuid(jwt == null ? null : jwt.getClaimAsString("sid"), "sid");
    }

    private UUID parseUuid(String value, String claim) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new AuthApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "INVALID_ACCESS_TOKEN",
                    "Access token is missing the required " + claim + " claim");
        }
    }
}
