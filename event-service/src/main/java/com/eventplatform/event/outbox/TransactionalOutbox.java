package com.eventplatform.event.outbox;

import com.eventplatform.event.api.EventApiException;
import com.eventplatform.event.api.RequestContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TransactionalOutbox {

    private final OutboxMessageRepository repository;
    private final ObjectMapper objectMapper;

    public TransactionalOutbox(OutboxMessageRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void append(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            Object payload,
            RequestContext requestContext,
            Instant occurredAt) {
        try {
            repository.save(new OutboxMessage(
                    UUID.randomUUID(),
                    aggregateType,
                    aggregateId,
                    eventType,
                    eventVersion,
                    objectMapper.writeValueAsString(payload),
                    requestContext.correlationId(),
                    requestContext.traceparent(),
                    occurredAt));
        } catch (JsonProcessingException exception) {
            throw new EventApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "OUTBOX_SERIALIZATION_FAILED",
                    "The lifecycle change could not be recorded");
        }
    }
}
