package com.eventplatform.payment.integration;

import com.eventplatform.contracts.KafkaEventMetadata;
import com.eventplatform.payment.api.RequestContext;
import com.eventplatform.payment.application.RefundApplicationService;
import com.eventplatform.payment.domain.BookingPaymentOrder;
import com.eventplatform.payment.domain.BookingPaymentOrderRepository;
import com.eventplatform.payment.domain.PaymentRepository;
import com.eventplatform.payment.domain.PaymentTicketProjection;
import com.eventplatform.payment.domain.PaymentTicketProjectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "platform.integration-events.enabled", havingValue = "true", matchIfMissing = true)
public class AttendeeLifecycleConsumer {
    public static final String PAYMENT_REQUESTED = "event-platform.booking.payment-requested.v1";
    public static final String TICKET_ISSUED = "event-platform.ticket.issued.v1";
    public static final String TICKET_CHECKED_IN = "event-platform.ticket.checked-in.v1";
    public static final String COMPENSATE = "event-platform.payment.compensation-requested.v1";

    private final ProcessedIntegrationEventRepository processed;
    private final BookingPaymentOrderRepository orders;
    private final PaymentTicketProjectionRepository tickets;
    private final PaymentRepository payments;
    private final RefundApplicationService refundApplication;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AttendeeLifecycleConsumer(
            ProcessedIntegrationEventRepository processed,
            BookingPaymentOrderRepository orders,
            PaymentTicketProjectionRepository tickets,
            PaymentRepository payments,
            RefundApplicationService refundApplication,
            ObjectMapper mapper,
            Clock clock) {
        this.processed = processed;
        this.orders = orders;
        this.tickets = tickets;
        this.payments = payments;
        this.refundApplication = refundApplication;
        this.mapper = mapper;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${platform.integration-events.attendee-topic:event-platform.attendee-lifecycle.v1}",
            groupId = "${spring.kafka.consumer.group-id:payment-service-v1}")
    @Transactional
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        KafkaEventMetadata metadata = KafkaEventMetadata.from(record.headers());
        if (metadata.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported event schema version " + metadata.schemaVersion());
        }
        UUID messageId = metadata.messageId();
        if (processed.existsById(messageId)) return;
        JsonNode body = mapper.readTree(record.value());
        Instant now = clock.instant();
        RequestContext context = new RequestContext(metadata.correlationId(), metadata.traceparent());
        switch (metadata.eventType()) {
            case PAYMENT_REQUESTED -> consumePaymentOrder(body, now);
            case TICKET_ISSUED -> consumeTicketIssued(body);
            case TICKET_CHECKED_IN -> consumeTicketCheckedIn(body);
            case COMPENSATE -> payments.findByBookingId(uuid(body, "bookingId"))
                    .ifPresent(payment -> refundApplication.compensate(
                            payment.getId(), "compensation:" + body.required("bookingId").asText(), context));
            default -> { }
        }
        processed.save(new ProcessedIntegrationEvent(messageId, metadata.eventType(), now));
    }

    private void consumePaymentOrder(JsonNode body, Instant now) {
        UUID bookingId = uuid(body, "bookingId");
        if (orders.existsById(bookingId)) return;
        orders.save(new BookingPaymentOrder(
                bookingId, uuid(body, "attendeeId"), uuid(body, "eventId"), uuid(body, "eventOrganizerId"),
                uuid(body, "inventoryReservationId"), uuid(body, "ticketTypeId"),
                body.required("quantity").asInt(), new BigDecimal(body.required("unitPrice").asText()),
                new BigDecimal(body.required("totalAmount").asText()), body.required("currency").asText(),
                Instant.parse(body.required("eventStartsAt").asText()),
                Instant.parse(body.required("holdExpiresAt").asText()), text(body, "eventTitle"),
                text(body, "attendeeEmail"), text(body, "attendeePhone"),
                body.path("attendeeLocale").asText("en"), now));
    }

    private void consumeTicketIssued(JsonNode body) {
        UUID ticketId = uuid(body, "ticketId");
        if (!tickets.existsById(ticketId)) {
            tickets.save(new PaymentTicketProjection(
                    ticketId, uuid(body, "bookingId"), uuid(body, "attendeeId"), uuid(body, "eventId"),
                    Instant.parse(body.required("issuedAt").asText())));
        }
    }

    private void consumeTicketCheckedIn(JsonNode body) {
        UUID ticketId = uuid(body, "ticketId");
        PaymentTicketProjection ticket = tickets.findById(ticketId).orElseGet(() -> new PaymentTicketProjection(
                ticketId, uuid(body, "bookingId"), uuid(body, "attendeeId"), uuid(body, "eventId"),
                Instant.parse(body.required("checkedInAt").asText())));
        ticket.checkedIn(Instant.parse(body.required("checkedInAt").asText()));
        tickets.save(ticket);
    }

    private UUID uuid(JsonNode node, String name) { return UUID.fromString(node.required(name).asText()); }
    private String text(JsonNode node, String name) {
        return node.path(name).isMissingNode() || node.path(name).isNull() ? null : node.path(name).asText();
    }
}
