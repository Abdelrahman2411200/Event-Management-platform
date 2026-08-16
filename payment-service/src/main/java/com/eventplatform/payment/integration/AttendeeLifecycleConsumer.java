package com.eventplatform.payment.integration;

import com.eventplatform.contracts.CorrelationIds; import com.eventplatform.payment.api.RequestContext; import com.eventplatform.payment.application.RefundApplicationService; import com.eventplatform.payment.domain.*; import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal; import java.time.*; import java.util.UUID; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.kafka.annotation.KafkaListener; import org.springframework.messaging.handler.annotation.*; import org.springframework.stereotype.Component; import org.springframework.transaction.annotation.Transactional;

@Component @ConditionalOnProperty(name="platform.integration-events.enabled",havingValue="true",matchIfMissing=true)
public class AttendeeLifecycleConsumer {
 public static final String PAYMENT_REQUESTED="event-platform.booking.payment-requested.v1",TICKET_ISSUED="event-platform.ticket.issued.v1",TICKET_CHECKED_IN="event-platform.ticket.checked-in.v1",COMPENSATE="event-platform.payment.compensation-requested.v1";
 private final ProcessedIntegrationEventRepository processed; private final BookingPaymentOrderRepository orders; private final PaymentTicketProjectionRepository tickets; private final PaymentRepository payments; private final RefundApplicationService refundApplication; private final ObjectMapper mapper; private final Clock clock;
 public AttendeeLifecycleConsumer(ProcessedIntegrationEventRepository processed,BookingPaymentOrderRepository orders,PaymentTicketProjectionRepository tickets,PaymentRepository payments,RefundApplicationService refundApplication,ObjectMapper mapper,Clock clock){this.processed=processed;this.orders=orders;this.tickets=tickets;this.payments=payments;this.refundApplication=refundApplication;this.mapper=mapper;this.clock=clock;}
 @KafkaListener(topics="${platform.integration-events.attendee-topic:event-platform.attendee-lifecycle.v1}",groupId="${spring.kafka.consumer.group-id:payment-service-v1}") @Transactional
 public void consume(@Payload String payload,@Header("eventId") String eventId,@Header("eventType") String eventType,@Header(name=CorrelationIds.KAFKA_HEADER,required=false) String correlation,@Header(name=CorrelationIds.TRACEPARENT_HEADER,required=false) String traceparent)throws Exception{
  UUID messageId=UUID.fromString(eventId);if(processed.existsById(messageId))return;JsonNode n=mapper.readTree(payload);Instant now=clock.instant();RequestContext context=new RequestContext(correlation==null?"kafka:"+messageId:correlation,traceparent);
  switch(eventType){
   case PAYMENT_REQUESTED -> {UUID booking=uuid(n,"bookingId");if(!orders.existsById(booking))orders.save(new BookingPaymentOrder(booking,uuid(n,"attendeeId"),uuid(n,"eventId"),uuid(n,"eventOrganizerId"),uuid(n,"inventoryReservationId"),uuid(n,"ticketTypeId"),n.required("quantity").asInt(),new BigDecimal(n.required("unitPrice").asText()),new BigDecimal(n.required("totalAmount").asText()),n.required("currency").asText(),Instant.parse(n.required("eventStartsAt").asText()),Instant.parse(n.required("holdExpiresAt").asText()),now));}
   case TICKET_ISSUED -> {UUID id=uuid(n,"ticketId");if(!tickets.existsById(id))tickets.save(new PaymentTicketProjection(id,uuid(n,"bookingId"),uuid(n,"attendeeId"),uuid(n,"eventId"),Instant.parse(n.required("issuedAt").asText())));}
   case TICKET_CHECKED_IN -> {UUID id=uuid(n,"ticketId");PaymentTicketProjection ticket=tickets.findById(id).orElseGet(()->new PaymentTicketProjection(id,uuid(n,"bookingId"),uuid(n,"attendeeId"),uuid(n,"eventId"),Instant.parse(n.required("checkedInAt").asText())));ticket.checkedIn(Instant.parse(n.required("checkedInAt").asText()));tickets.save(ticket);}
   case COMPENSATE -> payments.findByBookingId(uuid(n,"bookingId")).ifPresent(p->refundApplication.compensate(p.getId(),"compensation:"+n.required("bookingId").asText(),context));
   default -> {}
  }
  processed.save(new ProcessedIntegrationEvent(messageId,eventType,now));
 }
 private UUID uuid(JsonNode n,String name){return UUID.fromString(n.required(name).asText());}
}
