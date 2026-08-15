package com.eventplatform.event.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findAllByEventIdOrderByPriceAscNameAsc(UUID eventId);

    List<TicketType> findAllByEventIdAndStatusInOrderByPriceAscNameAsc(
            UUID eventId, Collection<TicketTypeStatus> statuses);

    Optional<TicketType> findByIdAndEventId(UUID id, UUID eventId);

    boolean existsByEventIdAndNameIgnoreCaseAndStatusNot(
            UUID eventId, String name, TicketTypeStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from TicketType ticket where ticket.id = :id and ticket.eventId = :eventId")
    Optional<TicketType> findByIdAndEventIdForUpdate(
            @Param("id") UUID id,
            @Param("eventId") UUID eventId);
}
