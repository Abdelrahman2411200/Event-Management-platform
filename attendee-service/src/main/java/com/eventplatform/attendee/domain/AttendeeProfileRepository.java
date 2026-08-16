package com.eventplatform.attendee.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendeeProfileRepository extends JpaRepository<AttendeeProfile, UUID> {
}
