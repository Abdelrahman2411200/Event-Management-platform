package com.eventplatform.notification.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationIntentRepository extends JpaRepository<NotificationIntent, UUID> {
    Optional<NotificationIntent> findByNotificationKey(String notificationKey);
    List<NotificationIntent> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<NotificationIntent> findAllByBusinessIdAndType(UUID businessId, NotificationType type);
    List<NotificationIntent> findAllByUserIdAndTypeAndStatusIn(
            UUID userId, NotificationType type, Collection<NotificationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select intent from NotificationIntent intent
            where intent.status in :statuses and intent.nextAttemptAt <= :now
            order by intent.nextAttemptAt, intent.createdAt
            """)
    List<NotificationIntent> findDueForUpdate(
            @Param("statuses") Collection<NotificationStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable);
}
