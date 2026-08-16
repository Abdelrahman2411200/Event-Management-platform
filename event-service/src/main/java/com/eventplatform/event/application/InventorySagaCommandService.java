package com.eventplatform.event.application;

import com.eventplatform.event.api.RequestContext;
import java.util.UUID; import org.springframework.stereotype.Service;

@Service
public class InventorySagaCommandService {
 private final InventoryService inventory;
 public InventorySagaCommandService(InventoryService inventory){this.inventory=inventory;}
 public void confirm(Command c,RequestContext context){inventory.confirmSaga(c.bookingId(),c.paymentId(),c.attendeeId(),c.eventId(),c.ticketTypeId(),c.inventoryReservationId(),c.commandKey(),context);}
 public void release(Command c,RequestContext context){inventory.releaseSaga(c.bookingId(),c.paymentId(),c.attendeeId(),c.eventId(),c.ticketTypeId(),c.inventoryReservationId(),c.commandKey(),context);}
 public record Command(UUID bookingId,UUID paymentId,UUID attendeeId,UUID eventId,UUID ticketTypeId,UUID inventoryReservationId,int quantity,String commandKey){}
}
