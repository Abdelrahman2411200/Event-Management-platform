package com.eventplatform.event.integration;

import com.eventplatform.contracts.CorrelationIds; import com.eventplatform.event.api.RequestContext; import com.eventplatform.event.application.InventorySagaCommandService; import com.fasterxml.jackson.databind.*; import java.time.Clock; import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.kafka.annotation.KafkaListener; import org.springframework.messaging.handler.annotation.*; import org.springframework.stereotype.Component; import org.springframework.transaction.annotation.Transactional;

@Component @ConditionalOnProperty(name="platform.integration-events.enabled",havingValue="true",matchIfMissing=true)
public class AttendeeInventorySagaConsumer {
 private static final String CONFIRM="event-platform.inventory.confirmation-requested.v1",RELEASE="event-platform.inventory.release-requested.v1";
 private final ProcessedIntegrationEventRepository processed;private final InventorySagaCommandService service;private final ObjectMapper mapper;private final Clock clock;
 public AttendeeInventorySagaConsumer(ProcessedIntegrationEventRepository processed,InventorySagaCommandService service,ObjectMapper mapper,Clock clock){this.processed=processed;this.service=service;this.mapper=mapper;this.clock=clock;}
 @KafkaListener(topics="${platform.integration-events.attendee-topic:event-platform.attendee-lifecycle.v1}",groupId="${spring.kafka.consumer.group-id:event-service-inventory-saga-v1}") @Transactional
 public void consume(@Payload String payload,@Header("eventId") String eventId,@Header("eventType") String type,@Header(name=CorrelationIds.KAFKA_HEADER,required=false) String correlation,@Header(name=CorrelationIds.TRACEPARENT_HEADER,required=false) String traceparent)throws Exception{UUID id=UUID.fromString(eventId);if(processed.existsById(id))return;if(CONFIRM.equals(type)||RELEASE.equals(type)){JsonNode n=mapper.readTree(payload);InventorySagaCommandService.Command c=new InventorySagaCommandService.Command(uuid(n,"bookingId"),uuidOrNull(n,"paymentId"),uuid(n,"attendeeId"),uuid(n,"eventId"),uuid(n,"ticketTypeId"),uuid(n,"inventoryReservationId"),n.required("quantity").asInt(),n.required("commandKey").asText());RequestContext context=new RequestContext(correlation==null?"kafka:"+id:correlation,traceparent);if(CONFIRM.equals(type))service.confirm(c,context);else service.release(c,context);}processed.save(new ProcessedIntegrationEvent(id,type,clock.instant()));}
 private UUID uuid(JsonNode n,String name){return UUID.fromString(n.required(name).asText());}private UUID uuidOrNull(JsonNode n,String name){return n.path(name).isNull()||n.path(name).isMissingNode()?null:UUID.fromString(n.path(name).asText());}
}
