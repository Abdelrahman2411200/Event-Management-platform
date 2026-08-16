package com.eventplatform.attendee.application;

import com.eventplatform.attendee.api.RequestContext;
import com.eventplatform.attendee.domain.TicketHoldRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "platform.holds.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class TicketHoldExpiryJob {
    private final TicketHoldRepository holdRepository;
    private final BookingPersistenceService persistenceService;
    private final Clock clock;

    public TicketHoldExpiryJob(
            TicketHoldRepository holdRepository, BookingPersistenceService persistenceService, Clock clock) {
        this.holdRepository = holdRepository;
        this.persistenceService = persistenceService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${platform.holds.expiry-interval:30s}")
    public void expireHolds() {
        for (UUID holdId : holdRepository.findExpiredIds(clock.instant(), PageRequest.of(0, 100))) {
            persistenceService.expireByHoldId(holdId, RequestContext.system("ticket-hold-expiry", holdId.toString()));
        }
    }
}
