package com.eventplatform.payment.security;

import com.eventplatform.payment.api.PaymentApiException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public record AuthenticatedActor(UUID id, Set<String> roles) {
    public static AuthenticatedActor from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt))
            throw new PaymentApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required");
        try {
            Set<String> roles = authentication.getAuthorities().stream().map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).collect(Collectors.toSet());
            return new AuthenticatedActor(UUID.fromString(jwt.getSubject()), roles);
        } catch (IllegalArgumentException exception) {
            throw new PaymentApiException(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "The access token subject is invalid");
        }
    }
    public boolean hasRole(String role) { return roles.contains(role); }
}
