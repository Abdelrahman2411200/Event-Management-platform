package com.eventplatform.event.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    Optional<InventoryReservation> findByReserveIdempotencyKey(String idempotencyKey);

    Optional<InventoryReservation> findByReleaseIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from InventoryReservation reservation where reservation.id = :id")
    Optional<InventoryReservation> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select reservation from InventoryReservation reservation
            where reservation.ticketTypeId = :ticketTypeId
              and reservation.status = com.eventplatform.event.domain.InventoryReservationStatus.ACTIVE
              and reservation.expiresAt <= :now
            order by reservation.expiresAt
            """)
    List<InventoryReservation> findExpiredActive(
            @Param("ticketTypeId") UUID ticketTypeId,
            @Param("now") Instant now);

    @Query("""
            select reservation.id from InventoryReservation reservation
            where reservation.status = com.eventplatform.event.domain.InventoryReservationStatus.ACTIVE
              and reservation.expiresAt <= :now
            order by reservation.expiresAt
            """)
    List<UUID> findExpiredIds(@Param("now") Instant now, org.springframework.data.domain.Pageable pageable);
}
