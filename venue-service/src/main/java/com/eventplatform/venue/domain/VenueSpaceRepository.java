package com.eventplatform.venue.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueSpaceRepository extends JpaRepository<VenueSpace, UUID> {

    List<VenueSpace> findAllByVenueIdOrderByNameAsc(UUID venueId);

    List<VenueSpace> findAllByVenueIdAndStatusOrderByNameAsc(UUID venueId, VenueSpaceStatus status);

    Optional<VenueSpace> findByIdAndVenueId(UUID id, UUID venueId);

    boolean existsByVenueIdAndNameIgnoreCase(UUID venueId, String name);
}
