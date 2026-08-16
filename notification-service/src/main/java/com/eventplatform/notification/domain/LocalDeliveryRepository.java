package com.eventplatform.notification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalDeliveryRepository extends JpaRepository<LocalDelivery, UUID> {
    Optional<LocalDelivery> findByIdempotencyKey(String idempotencyKey);
    List<LocalDelivery> findTop100ByOrderByCreatedAtDesc();
}
