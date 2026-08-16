package com.eventplatform.attendee.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInAttemptRepository extends JpaRepository<CheckInAttempt, UUID> {
    Optional<CheckInAttempt> findByScannerIdAndOperationAndIdempotencyKey(
            UUID scannerId, ScanOperation operation, String idempotencyKey);
}
