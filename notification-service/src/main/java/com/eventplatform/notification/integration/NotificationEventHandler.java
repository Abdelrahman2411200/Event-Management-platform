package com.eventplatform.notification.integration;

import com.eventplatform.contracts.KafkaEventMetadata;
import com.eventplatform.notification.application.NotificationPlanner;
import com.eventplatform.notification.domain.BookingNotificationProjection;
import com.eventplatform.notification.domain.BookingNotificationProjectionRepository;
import com.eventplatform.notification.domain.NotificationRecipient;
import com.eventplatform.notification.domain.NotificationRecipientRepository;
import com.eventplatform.notification.domain.NotificationType;
import com.eventplatform.notification.domain.ProcessedIntegrationEvent;
import com.eventplatform.notification.domain.ProcessedIntegrationEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationEventHandler {
    static final String BOOKING_CREATED = "event-platform.booking.created.v1";
    static final String BOOKING_CONFIRMED = "event-platform.booking.confirmed.v1";
    static final String PAYMENT_SUCCEEDED = "event-platform.payment.succeeded.v1";
    static final String PAYMENT_FAILED = "event-platform.payment.failed.v1";
    static final String TICKET_ISSUED = "event-platform.ticket.issued.v1";
    static final String EVENT_UPDATED = "event-platform.event.updated.v1";
    static final String EVENT_CANCELLED = "event-platform.event.cancelled.v1";
    static final String REFUND_SUCCEEDED = "event-platform.refund.succeeded.v1";

    private final ProcessedIntegrationEventRepository processed;
    private final NotificationRecipientRepository recipients;
    private final BookingNotificationProjectionRepository bookings;
    private final NotificationPlanner planner;
    private final Clock clock;

    public NotificationEventHandler(
            ProcessedIntegrationEventRepository processed,
            NotificationRecipientRepository recipients,
            BookingNotificationProjectionRepository bookings,
            NotificationPlanner planner,
            Clock clock) {
        this.processed = processed;
        this.recipients = recipients;
        this.bookings = bookings;
        this.planner = planner;
        this.clock = clock;
    }

    @Transactional
    public void handle(KafkaEventMetadata metadata, JsonNode body) {
        if (metadata.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported event schema version " + metadata.schemaVersion());
        }
        if (processed.existsById(metadata.messageId())) return;
        switch (metadata.eventType()) {
            case BOOKING_CREATED -> bookingCreated(metadata, body);
            case BOOKING_CONFIRMED -> bookingConfirmed(metadata, body);
            case PAYMENT_SUCCEEDED -> payment(metadata, body, NotificationType.PAYMENT_CONFIRMED);
            case PAYMENT_FAILED -> payment(metadata, body, NotificationType.PAYMENT_FAILED);
            case TICKET_ISSUED -> ticketIssued(metadata, body);
            case EVENT_UPDATED -> eventUpdated(metadata, body);
            case EVENT_CANCELLED -> eventCancelled(metadata, body);
            case REFUND_SUCCEEDED -> refund(metadata, body);
            default -> { }
        }
        processed.save(new ProcessedIntegrationEvent(
                metadata.messageId(), metadata.eventType(), clock.instant()));
    }

    private void bookingCreated(KafkaEventMetadata metadata, JsonNode body) {
        UUID bookingId = uuid(body, "bookingId");
        UUID attendeeId = uuid(body, "attendeeId");
        String eventTitle = requiredText(body, "eventTitle");
        Instant startsAt = Instant.parse(body.required("eventStartsAt").asText());
        NotificationRecipient recipient = recipient(body, attendeeId);
        planner.booking(bookingId, attendeeId, uuid(body, "eventId"), eventTitle, startsAt);
        Map<String, String> variables = baseVariables(bookingId, eventTitle, startsAt);
        planner.plan(metadata.messageId(), recipient, bookingId,
                NotificationType.BOOKING_RECEIVED, variables, clock.instant());
        if (!"EXPIRED".equals(body.path("status").asText()) && startsAt.isAfter(clock.instant())) {
            planner.plan(metadata.messageId(), recipient, bookingId, NotificationType.EVENT_REMINDER,
                    variables, planner.reminderAt(startsAt, clock.instant()));
        }
    }

    private void bookingConfirmed(KafkaEventMetadata metadata, JsonNode body) {
        UUID bookingId = uuid(body, "bookingId");
        BookingNotificationProjection booking = booking(bookingId);
        NotificationRecipient recipient = recipients.findById(booking.getAttendeeId()).orElseThrow();
        planner.plan(metadata.messageId(), recipient, bookingId, NotificationType.BOOKING_CONFIRMED,
                baseVariables(bookingId, booking.getEventTitle(), booking.getEventStartsAt()), clock.instant());
    }

    private void payment(KafkaEventMetadata metadata, JsonNode body, NotificationType type) {
        UUID bookingId = uuid(body, "bookingId");
        UUID attendeeId = uuid(body, "attendeeId");
        NotificationRecipient recipient = recipient(body, attendeeId);
        String eventTitle = eventTitle(body, bookingId);
        Map<String, String> variables = baseVariables(
                bookingId, eventTitle, optionalInstant(body, "eventStartsAt"));
        variables.put("amount", body.required("amount").asText());
        variables.put("currency", body.required("currency").asText());
        variables.put("failureReason", body.path("failureReason").asText("Payment was declined"));
        planner.plan(metadata.messageId(), recipient, uuid(body, "paymentId"), type, variables, clock.instant());
    }

    private void ticketIssued(KafkaEventMetadata metadata, JsonNode body) {
        UUID bookingId = uuid(body, "bookingId");
        UUID attendeeId = uuid(body, "attendeeId");
        String eventTitle = requiredText(body, "eventTitle");
        Instant startsAt = Instant.parse(body.required("eventStartsAt").asText());
        NotificationRecipient recipient = recipient(body, attendeeId);
        planner.booking(bookingId, attendeeId, uuid(body, "eventId"), eventTitle, startsAt);
        Map<String, String> variables = baseVariables(bookingId, eventTitle, startsAt);
        variables.put("ticketId", body.required("ticketId").asText());
        variables.put("ticketTypeName", requiredText(body, "ticketTypeName"));
        variables.put("qrToken", requiredText(body, "qrToken"));
        planner.plan(metadata.messageId(), recipient, uuid(body, "ticketId"),
                NotificationType.TICKET_ISSUED, variables, clock.instant());
    }

    private void eventUpdated(KafkaEventMetadata metadata, JsonNode body) {
        planner.rescheduleEvent(
                metadata.messageId(), uuid(body, "eventId"),
                Instant.parse(body.required("startsAt").asText()));
    }

    private void eventCancelled(KafkaEventMetadata metadata, JsonNode body) {
        UUID eventId = uuid(body, "eventId");
        planner.cancelRemindersForEvent(eventId);
        for (BookingNotificationProjection booking : bookings.findAllByEventId(eventId)) {
            NotificationRecipient recipient = recipients.findById(booking.getAttendeeId()).orElseThrow();
            planner.plan(metadata.messageId(), recipient, booking.getBookingId(), NotificationType.EVENT_CANCELLED,
                    baseVariables(booking.getBookingId(), booking.getEventTitle(), booking.getEventStartsAt()),
                    clock.instant());
        }
    }

    private void refund(KafkaEventMetadata metadata, JsonNode body) {
        UUID bookingId = uuid(body, "bookingId");
        UUID attendeeId = uuid(body, "attendeeId");
        NotificationRecipient recipient = recipient(body, attendeeId);
        Map<String, String> variables = baseVariables(
                bookingId, eventTitle(body, bookingId), optionalInstant(body, "eventStartsAt"));
        variables.put("amount", body.required("amount").asText());
        variables.put("currency", body.required("currency").asText());
        planner.plan(metadata.messageId(), recipient, uuid(body, "refundId"),
                NotificationType.REFUND_CONFIRMED, variables, clock.instant());
    }

    private NotificationRecipient recipient(JsonNode body, UUID attendeeId) {
        return planner.recipient(
                attendeeId,
                optionalText(body, "attendeeEmail"),
                optionalText(body, "attendeePhone"),
                body.path("attendeeLocale").asText("en"),
                optionalText(body, "attendeeDisplayName"));
    }

    private BookingNotificationProjection booking(UUID bookingId) {
        return bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking notification projection is not ready"));
    }

    private String eventTitle(JsonNode body, UUID bookingId) {
        String supplied = optionalText(body, "eventTitle");
        return supplied == null ? booking(bookingId).getEventTitle() : supplied;
    }

    private Map<String, String> baseVariables(UUID bookingId, String eventTitle, Instant startsAt) {
        Map<String, String> variables = new HashMap<>();
        variables.put("bookingId", bookingId.toString());
        variables.put("eventTitle", eventTitle);
        variables.put("eventStartsAt", startsAt == null ? "to be announced" : startsAt.toString());
        return variables;
    }

    private UUID uuid(JsonNode node, String name) { return UUID.fromString(node.required(name).asText()); }
    private String requiredText(JsonNode node, String name) {
        String value = optionalText(node, name);
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }
    private String optionalText(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
    private Instant optionalInstant(JsonNode node, String name) {
        String value = optionalText(node, name);
        return value == null ? null : Instant.parse(value);
    }
}
