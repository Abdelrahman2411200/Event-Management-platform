package com.eventplatform.notification.api;

import com.eventplatform.notification.application.NotificationPreferenceService;
import com.eventplatform.notification.application.NotificationQueryService;
import com.eventplatform.notification.security.AuthenticatedActor;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationPreferenceService preferences;
    private final NotificationQueryService queries;

    public NotificationController(
            NotificationPreferenceService preferences, NotificationQueryService queries) {
        this.preferences = preferences;
        this.queries = queries;
    }

    @GetMapping("/preferences")
    public NotificationApi.PreferenceResponse preferences(JwtAuthenticationToken authentication) {
        return preferences.get(AuthenticatedActor.from(authentication).id());
    }

    @PutMapping("/preferences")
    public NotificationApi.PreferenceResponse updatePreferences(
            @RequestBody NotificationApi.PreferenceRequest request,
            JwtAuthenticationToken authentication) {
        return preferences.update(AuthenticatedActor.from(authentication).id(), request);
    }

    @GetMapping
    public List<NotificationApi.IntentResponse> notifications(JwtAuthenticationToken authentication) {
        return queries.intents(AuthenticatedActor.from(authentication).id());
    }

    @GetMapping("/local-deliveries")
    @PreAuthorize("hasRole('ADMIN')")
    public List<NotificationApi.LocalDeliveryResponse> localDeliveries() {
        return queries.localDeliveries();
    }
}
