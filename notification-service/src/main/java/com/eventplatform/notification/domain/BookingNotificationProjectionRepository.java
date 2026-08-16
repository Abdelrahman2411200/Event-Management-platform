package com.eventplatform.notification.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingNotificationProjectionRepository
        extends JpaRepository<BookingNotificationProjection, UUID> {
    List<BookingNotificationProjection> findAllByEventId(UUID eventId);
    List<BookingNotificationProjection> findAllByAttendeeId(UUID attendeeId);
}
