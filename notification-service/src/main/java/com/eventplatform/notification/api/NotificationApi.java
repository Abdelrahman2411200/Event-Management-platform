package com.eventplatform.notification.api;

import com.eventplatform.notification.domain.NotificationChannel;
import com.eventplatform.notification.domain.NotificationStatus;
import com.eventplatform.notification.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

public final class NotificationApi {
    private NotificationApi() {
    }

    public record PreferenceRequest(boolean remindersEnabled, boolean smsEnabled) { }
    public record PreferenceResponse(
            UUID userId, boolean remindersEnabled, boolean smsEnabled, Instant updatedAt,
            String mandatoryRule) { }
    public record IntentResponse(
            UUID id, NotificationType type, NotificationChannel channel, NotificationStatus status,
            UUID businessId, Instant scheduledAt, Instant sentAt, int attempts, String lastError) { }
    public record LocalDeliveryResponse(
            UUID id, String idempotencyKey, NotificationChannel channel, String destination,
            String subject, String body, Instant createdAt) { }
}
