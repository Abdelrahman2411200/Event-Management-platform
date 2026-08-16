package com.eventplatform.attendee.outbox;

import com.eventplatform.attendee.api.AttendeeApiException;
import com.eventplatform.attendee.api.RequestContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AttendeeTransactionalOutbox {

    private final AttendeeOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public AttendeeTransactionalOutbox(AttendeeOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void append(
            String aggregateType, UUID aggregateId, String eventType, int eventVersion,
            Object payload, RequestContext context, Instant occurredAt) {
        try {
            repository.save(new AttendeeOutboxMessage(
                    UUID.randomUUID(), aggregateType, aggregateId, eventType, eventVersion,
                    objectMapper.writeValueAsString(payload), context.correlationId(), context.traceparent(), occurredAt));
        } catch (JsonProcessingException exception) {
            throw new AttendeeApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "OUTBOX_SERIALIZATION_FAILED",
                    "The attendee lifecycle change could not be recorded");
        }
    }
}
