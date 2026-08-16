package com.eventplatform.attendee.application;

import com.eventplatform.attendee.api.AttendeeApi;
import com.eventplatform.attendee.api.RequestContext;
import com.eventplatform.attendee.domain.BookingCommand;
import com.eventplatform.attendee.domain.BookingCommandStatus;
import com.eventplatform.attendee.security.AuthenticatedActor;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class BookingCoordinator {
    private final AttendeeProfileService profileService;
    private final BookingCommandService commandService;
    private final EventInventoryPort inventoryPort;
    private final BookingPersistenceService persistenceService;

    public BookingCoordinator(
            AttendeeProfileService profileService,
            BookingCommandService commandService,
            EventInventoryPort inventoryPort,
            BookingPersistenceService persistenceService) {
        this.profileService = profileService;
        this.commandService = commandService;
        this.inventoryPort = inventoryPort;
        this.persistenceService = persistenceService;
    }

    public AttendeeApi.BookingResponse create(
            AttendeeApi.CreateBookingRequest request,
            String idempotencyKey,
            AuthenticatedActor actor,
            RequestContext context) {
        profileService.ensure(actor);
        BookingCommand command = commandService.claim(
                actor.userId(), idempotencyKey, request.eventId(), request.ticketTypeId(), request.quantity());
        if (command.getStatus() == BookingCommandStatus.COMPLETED) {
            return persistenceService.completed(command.getId(), actor.userId());
        }
        String reserveKey = "attendee-reserve:" + command.getId();
        EventInventoryPort.InventoryHold hold = inventoryPort.reserve(
                command.getEventId(), command.getTicketTypeId(), command.getQuantity(), reserveKey, context);
        BigDecimal total = hold.unitPrice().multiply(BigDecimal.valueOf(hold.quantity()));
        if (total.signum() == 0 && hold.status() == EventInventoryPort.InventoryStatus.ACTIVE) {
            hold = inventoryPort.confirm(
                    command.getEventId(), command.getTicketTypeId(), hold.id(),
                    "attendee-confirm:" + command.getId(), context);
        }
        return persistenceService.complete(command.getId(), hold, context);
    }
}
