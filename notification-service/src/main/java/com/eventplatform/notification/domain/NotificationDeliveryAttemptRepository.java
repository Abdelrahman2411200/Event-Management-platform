package com.eventplatform.notification.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryAttemptRepository extends JpaRepository<NotificationDeliveryAttempt, UUID> {
    List<NotificationDeliveryAttempt> findAllByIntentIdOrderByAttemptNumber(UUID intentId);
}
