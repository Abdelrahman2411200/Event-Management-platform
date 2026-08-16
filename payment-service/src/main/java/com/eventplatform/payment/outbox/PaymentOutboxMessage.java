package com.eventplatform.payment.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity @Table(name="payment_outbox_messages")
public class PaymentOutboxMessage {
    @Id private UUID id; @Column(name="aggregate_type",nullable=false,length=80) private String aggregateType;
    @Column(name="aggregate_id",nullable=false) private UUID aggregateId; @Column(name="event_type",nullable=false,length=160) private String eventType;
    @Column(name="event_version",nullable=false) private int eventVersion; @Column(nullable=false,columnDefinition="TEXT") private String payload;
    @Column(name="correlation_id",nullable=false,length=128) private String correlationId; @Column(length=256) private String traceparent;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt; @Column(name="published_at") private Instant publishedAt;
    @Column(name="publish_attempts",nullable=false) private int publishAttempts; @Column(name="next_attempt_at",nullable=false) private Instant nextAttemptAt;
    @Column(name="last_error",length=500) private String lastError; @Version private long version;
    protected PaymentOutboxMessage(){}
    PaymentOutboxMessage(UUID id,String type,UUID aggregateId,String eventType,String payload,String correlationId,String traceparent,Instant at){this.id=id;aggregateType=type;this.aggregateId=aggregateId;this.eventType=eventType;eventVersion=1;this.payload=payload;this.correlationId=correlationId;this.traceparent=traceparent;occurredAt=at;nextAttemptAt=at;}
    void markPublished(Instant now){publishedAt=now;publishAttempts++;lastError=null;} void markFailed(String error,Instant now){publishAttempts++;nextAttemptAt=now.plus(Math.min(60,1L<<Math.min(publishAttempts,5)),ChronoUnit.SECONDS);lastError=error==null?"Kafka publication failed":error.substring(0,Math.min(500,error.length()));}
    UUID getId(){return id;} UUID getAggregateId(){return aggregateId;} String getEventType(){return eventType;} String getPayload(){return payload;} String getCorrelationId(){return correlationId;} String getTraceparent(){return traceparent;} Instant getOccurredAt(){return occurredAt;}
}
