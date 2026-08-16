package com.eventplatform.attendee.security;

import com.eventplatform.attendee.api.AttendeeApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record AuthenticatedActor(UUID userId, String email, Set<String> roles) {
    public AuthenticatedActor { roles = Set.copyOf(roles); }

    public AuthenticatedActor(UUID userId, Set<String> roles) {
        this(userId, null, roles);
    }

    public static AuthenticatedActor from(JwtAuthenticationToken authentication) {
        try {
            List<String> claims = authentication.getToken().getClaimAsStringList("roles");
            return new AuthenticatedActor(
                    UUID.fromString(authentication.getToken().getSubject()),
                    authentication.getToken().getClaimAsString("email"),
                    claims == null ? Set.of() : new HashSet<>(claims));
        } catch (RuntimeException exception) {
            throw new AttendeeApiException(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "The access token subject is invalid");
        }
    }

    public boolean isAdmin() { return roles.contains("ADMIN"); }
    public boolean isOrganizer() { return roles.contains("ORGANIZER"); }
    public boolean isEventStaff() { return roles.contains("EVENT_STAFF"); }
    public boolean owns(UUID attendeeId) { return userId.equals(attendeeId); }
    public boolean mayScan(UUID eventOrganizerId) {
        return isAdmin() || isEventStaff() || (isOrganizer() && userId.equals(eventOrganizerId));
    }
}
