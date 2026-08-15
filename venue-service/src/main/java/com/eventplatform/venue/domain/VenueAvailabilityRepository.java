package com.eventplatform.venue.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VenueAvailabilityRepository extends JpaRepository<VenueAvailabilityEntry, UUID> {

    Optional<VenueAvailabilityEntry> findByOwnerReference(String ownerReference);

    Optional<VenueAvailabilityEntry> findByIdAndVenueId(UUID id, UUID venueId);

    @Query("""
            select entry from VenueAvailabilityEntry entry
            where entry.venueId = :venueId
              and entry.status = com.eventplatform.venue.domain.AvailabilityStatus.ACTIVE
              and entry.startsAt < :endsAt
              and entry.endsAt > :startsAt
              and (:excludedOwner is null or entry.ownerReference is null or entry.ownerReference <> :excludedOwner)
            order by entry.startsAt
            """)
    List<VenueAvailabilityEntry> findWholeVenueConflicts(
            @Param("venueId") UUID venueId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("excludedOwner") String excludedOwner);

    @Query("""
            select entry from VenueAvailabilityEntry entry
            where entry.venueId = :venueId
              and entry.status = com.eventplatform.venue.domain.AvailabilityStatus.ACTIVE
              and entry.startsAt < :endsAt
              and entry.endsAt > :startsAt
              and (entry.venueSpaceId is null or entry.venueSpaceId = :spaceId)
              and (:excludedOwner is null or entry.ownerReference is null or entry.ownerReference <> :excludedOwner)
            order by entry.startsAt
            """)
    List<VenueAvailabilityEntry> findSpaceConflicts(
            @Param("venueId") UUID venueId,
            @Param("spaceId") UUID spaceId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("excludedOwner") String excludedOwner);

    @Query("""
            select count(entry) from VenueAvailabilityEntry entry
            where entry.venueId = :venueId
              and entry.kind = com.eventplatform.venue.domain.AvailabilityKind.EVENT_RESERVATION
              and entry.status = com.eventplatform.venue.domain.AvailabilityStatus.ACTIVE
              and entry.endsAt > :now
            """)
    long countActiveFutureReservations(@Param("venueId") UUID venueId, @Param("now") Instant now);

    @Query("""
            select max(entry.requiredCapacity) from VenueAvailabilityEntry entry
            where entry.venueId = :venueId
              and entry.venueSpaceId is null
              and entry.kind = com.eventplatform.venue.domain.AvailabilityKind.EVENT_RESERVATION
              and entry.status = com.eventplatform.venue.domain.AvailabilityStatus.ACTIVE
              and entry.endsAt > :now
            """)
    Integer maximumActiveFutureWholeVenueCapacity(
            @Param("venueId") UUID venueId,
            @Param("now") Instant now);

    @Query("""
            select max(entry.requiredCapacity) from VenueAvailabilityEntry entry
            where entry.venueSpaceId = :spaceId
              and entry.kind = com.eventplatform.venue.domain.AvailabilityKind.EVENT_RESERVATION
              and entry.status = com.eventplatform.venue.domain.AvailabilityStatus.ACTIVE
              and entry.endsAt > :now
            """)
    Integer maximumActiveFutureSpaceCapacity(
            @Param("spaceId") UUID spaceId,
            @Param("now") Instant now);

    @Query("""
            select count(entry) from VenueAvailabilityEntry entry
            where entry.venueSpaceId = :spaceId
              and entry.status = com.eventplatform.venue.domain.AvailabilityStatus.ACTIVE
              and entry.endsAt > :now
            """)
    long countActiveFutureEntriesForSpace(@Param("spaceId") UUID spaceId, @Param("now") Instant now);
}
