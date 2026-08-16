package com.eventplatform.attendee.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketHoldRepository extends JpaRepository<TicketHold, UUID> {

    Optional<TicketHold> findByBookingId(UUID bookingId);
    Optional<TicketHold> findByInventoryReservationId(UUID inventoryReservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hold from TicketHold hold where hold.id = :id")
    Optional<TicketHold> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hold from TicketHold hold where hold.inventoryReservationId = :reservationId")
    Optional<TicketHold> findByInventoryReservationIdForUpdate(@Param("reservationId") UUID reservationId);

    @Query("""
            select hold.id from TicketHold hold
            where hold.status = com.eventplatform.attendee.domain.TicketHoldStatus.ACTIVE
              and hold.expiresAt <= :now
            order by hold.expiresAt
            """)
    List<UUID> findExpiredIds(@Param("now") Instant now, Pageable pageable);
}
