package com.eventplatform.notification.application;

import com.eventplatform.notification.api.NotificationApi;
import com.eventplatform.notification.domain.NotificationPreference;
import com.eventplatform.notification.domain.NotificationPreferenceRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {
    private static final String MANDATORY_RULE =
            "Transactional booking, payment, ticket, cancellation, reschedule, and refund email cannot be disabled";
    private final NotificationPreferenceRepository repository;
    private final NotificationPlanner planner;
    private final Clock clock;

    public NotificationPreferenceService(
            NotificationPreferenceRepository repository, NotificationPlanner planner, Clock clock) {
        this.repository = repository;
        this.planner = planner;
        this.clock = clock;
    }

    @Transactional
    public NotificationApi.PreferenceResponse get(UUID userId) {
        return response(repository.findById(userId)
                .orElseGet(() -> repository.save(new NotificationPreference(userId, clock.instant()))));
    }

    @Transactional
    public NotificationApi.PreferenceResponse update(
            UUID userId, NotificationApi.PreferenceRequest request) {
        planner.applyPreference(userId, request.remindersEnabled(), request.smsEnabled());
        return response(repository.findById(userId).orElseThrow());
    }

    private NotificationApi.PreferenceResponse response(NotificationPreference preference) {
        return new NotificationApi.PreferenceResponse(
                preference.getUserId(), preference.isRemindersEnabled(), preference.isSmsEnabled(),
                preference.getUpdatedAt(), MANDATORY_RULE);
    }
}
