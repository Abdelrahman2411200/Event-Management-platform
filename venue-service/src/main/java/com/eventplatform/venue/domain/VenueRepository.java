package com.eventplatform.venue.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venue v where v.id = :id")
    Optional<Venue> findByIdForUpdate(@Param("id") UUID id);
}
