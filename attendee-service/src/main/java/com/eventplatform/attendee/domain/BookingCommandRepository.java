package com.eventplatform.attendee.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingCommandRepository extends JpaRepository<BookingCommand, UUID> {

    Optional<BookingCommand> findByAttendeeIdAndIdempotencyKey(UUID attendeeId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select command from BookingCommand command where command.id = :id")
    Optional<BookingCommand> findByIdForUpdate(@Param("id") UUID id);
}
