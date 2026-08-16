package com.eventplatform.notification.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record AuthenticatedActor(UUID id, Set<String> roles) {
    public AuthenticatedActor { roles = Set.copyOf(roles); }

    public static AuthenticatedActor from(JwtAuthenticationToken authentication) {
        List<String> roles = authentication.getToken().getClaimAsStringList("roles");
        return new AuthenticatedActor(
                UUID.fromString(authentication.getToken().getSubject()),
                roles == null ? Set.of() : new HashSet<>(roles));
    }
}
