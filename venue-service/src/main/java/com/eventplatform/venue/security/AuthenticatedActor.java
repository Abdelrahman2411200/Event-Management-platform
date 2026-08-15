package com.eventplatform.venue.security;

import com.eventplatform.venue.api.VenueApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record AuthenticatedActor(UUID userId, Set<String> roles) {

    public AuthenticatedActor {
        roles = Set.copyOf(roles);
    }

    public static AuthenticatedActor from(JwtAuthenticationToken authentication) {
        try {
            List<String> claims = authentication.getToken().getClaimAsStringList("roles");
            return new AuthenticatedActor(
                    UUID.fromString(authentication.getToken().getSubject()),
                    claims == null ? Set.of() : new HashSet<>(claims));
        } catch (RuntimeException exception) {
            throw new VenueApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_ACCESS_TOKEN",
                    "The access token subject is invalid");
        }
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    public boolean owns(UUID organizerId) {
        return userId.equals(organizerId);
    }
}
