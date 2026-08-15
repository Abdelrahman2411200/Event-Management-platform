package com.eventplatform.event.application;

import com.eventplatform.event.domain.InventoryReservationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "platform.inventory.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class InventoryExpiryJob {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryService inventoryService;

    public InventoryExpiryJob(
            InventoryReservationRepository reservationRepository,
            InventoryService inventoryService) {
        this.reservationRepository = reservationRepository;
        this.inventoryService = inventoryService;
    }

    @Scheduled(fixedDelayString = "${platform.inventory.expiry-interval:30s}")
    public void expireHolds() {
        for (UUID reservationId : reservationRepository.findExpiredIds(Instant.now(), PageRequest.of(0, 100))) {
            inventoryService.expireReservation(reservationId);
        }
    }
}
