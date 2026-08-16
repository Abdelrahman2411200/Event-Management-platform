package com.eventplatform.notification.application;

import com.eventplatform.notification.api.NotificationApi;
import com.eventplatform.notification.domain.LocalDeliveryRepository;
import com.eventplatform.notification.domain.NotificationIntentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationQueryService {
    private final NotificationIntentRepository intents;
    private final LocalDeliveryRepository deliveries;

    public NotificationQueryService(
            NotificationIntentRepository intents, LocalDeliveryRepository deliveries) {
        this.intents = intents;
        this.deliveries = deliveries;
    }

    @Transactional(readOnly = true)
    public List<NotificationApi.IntentResponse> intents(UUID userId) {
        return intents.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(intent -> new NotificationApi.IntentResponse(
                        intent.getId(), intent.getType(), intent.getChannel(), intent.getStatus(),
                        intent.getBusinessId(), intent.getScheduledAt(), intent.getSentAt(),
                        intent.getAttemptCount(), intent.getLastError()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationApi.LocalDeliveryResponse> localDeliveries() {
        return deliveries.findTop100ByOrderByCreatedAtDesc().stream()
                .map(delivery -> new NotificationApi.LocalDeliveryResponse(
                        delivery.getId(), delivery.getIdempotencyKey(), delivery.getChannel(),
                        delivery.getDestination(), delivery.getSubject(), delivery.getBody(), delivery.getCreatedAt()))
                .toList();
    }
}
