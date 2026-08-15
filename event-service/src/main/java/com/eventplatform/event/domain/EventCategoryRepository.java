package com.eventplatform.event.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventCategoryRepository extends JpaRepository<EventCategory, UUID> {

    Optional<EventCategory> findBySlugIgnoreCase(String slug);

    List<EventCategory> findAllByStatusOrderByNameAsc(CategoryStatus status);
}
