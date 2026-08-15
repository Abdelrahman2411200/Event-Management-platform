package com.eventplatform.event.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagedEventRepository
        extends JpaRepository<ManagedEvent, UUID>, JpaSpecificationExecutor<ManagedEvent> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from ManagedEvent event where event.id = :id")
    Optional<ManagedEvent> findByIdForUpdate(@Param("id") UUID id);

    Optional<ManagedEvent> findByIdAndStatusIn(UUID id, Collection<EventStatus> statuses);

    boolean existsByCategoryIdAndStatusNot(UUID categoryId, EventStatus status);

    List<ManagedEvent> findAllByCategoryId(UUID categoryId);
}
