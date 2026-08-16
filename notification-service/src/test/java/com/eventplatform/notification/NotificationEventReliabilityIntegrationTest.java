package com.eventplatform.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventplatform.contracts.KafkaEventHeaders;
import com.eventplatform.contracts.KafkaEventMetadata;
import com.eventplatform.notification.api.NotificationApi;
import com.eventplatform.notification.application.NotificationDeliveryJob;
import com.eventplatform.notification.application.NotificationPreferenceService;
import com.eventplatform.notification.domain.BookingNotificationProjectionRepository;
import com.eventplatform.notification.domain.LocalDeliveryRepository;
import com.eventplatform.notification.domain.NotificationIntent;
import com.eventplatform.notification.domain.NotificationIntentRepository;
import com.eventplatform.notification.domain.NotificationDeliveryAttemptRepository;
import com.eventplatform.notification.domain.NotificationPreferenceRepository;
import com.eventplatform.notification.domain.NotificationRecipientRepository;
import com.eventplatform.notification.domain.NotificationStatus;
import com.eventplatform.notification.domain.NotificationType;
import com.eventplatform.notification.domain.ProcessedIntegrationEventRepository;
import com.eventplatform.notification.integration.NotificationEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class NotificationEventReliabilityIntegrationTest {
    private static final String BOOKING_CREATED = "event-platform.booking.created.v1";

    @Autowired private NotificationEventConsumer consumer;
    @Autowired private NotificationDeliveryJob deliveryJob;
    @Autowired private NotificationPreferenceService preferenceService;
    @Autowired private NotificationIntentRepository intents;
    @Autowired private NotificationDeliveryAttemptRepository attempts;
    @Autowired private ProcessedIntegrationEventRepository processed;
    @Autowired private BookingNotificationProjectionRepository bookings;
    @Autowired private NotificationRecipientRepository recipients;
    @Autowired private NotificationPreferenceRepository preferences;
    @Autowired private LocalDeliveryRepository deliveries;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        deliveries.deleteAll();
        attempts.deleteAll();
        intents.deleteAll();
        processed.deleteAll();
        bookings.deleteAll();
        recipients.deleteAll();
        preferences.deleteAll();
    }

    @Test
    void consumingTheSameKafkaRecordTwiceIsIdempotent() throws Exception {
        UUID messageId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        ConsumerRecord<String, String> record = bookingRecord(messageId, bookingId, UUID.randomUUID());

        consumer.consume(record);
        consumer.consume(record);

        assertThat(processed.count()).isEqualTo(1);
        assertThat(intents.findAllByBusinessIdAndType(bookingId, NotificationType.BOOKING_RECEIVED)).hasSize(1);
        assertThat(intents.findAllByBusinessIdAndType(bookingId, NotificationType.EVENT_REMINDER)).hasSize(1);
    }

    @Test
    void logicalDuplicateWithANewMessageIdDoesNotCreateDuplicateNotifications() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID attendeeId = UUID.randomUUID();

        consumer.consume(bookingRecord(UUID.randomUUID(), bookingId, attendeeId));
        consumer.consume(bookingRecord(UUID.randomUUID(), bookingId, attendeeId));

        assertThat(processed.count()).isEqualTo(2);
        assertThat(intents.count()).isEqualTo(2);
    }

    @Test
    void disabledRemindersDoNotSuppressMandatoryBookingMessages() throws Exception {
        UUID attendeeId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        preferenceService.update(attendeeId, new NotificationApi.PreferenceRequest(false, false));

        consumer.consume(bookingRecord(UUID.randomUUID(), bookingId, attendeeId));

        assertThat(intents.findAllByBusinessIdAndType(bookingId, NotificationType.BOOKING_RECEIVED)).hasSize(1);
        assertThat(intents.findAllByBusinessIdAndType(bookingId, NotificationType.EVENT_REMINDER)).isEmpty();
    }

    @Test
    void reEnablingRemindersRestoresDurableSchedulesForExistingBookings() throws Exception {
        UUID attendeeId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        consumer.consume(bookingRecord(UUID.randomUUID(), bookingId, attendeeId));
        preferenceService.update(attendeeId, new NotificationApi.PreferenceRequest(false, false));
        NotificationIntent cancelled = intents.findAllByBusinessIdAndType(
                bookingId, NotificationType.EVENT_REMINDER).get(0);
        assertThat(cancelled.getStatus()).isEqualTo(NotificationStatus.CANCELLED);

        preferenceService.update(attendeeId, new NotificationApi.PreferenceRequest(true, false));

        NotificationIntent restored = intents.findAllByBusinessIdAndType(
                bookingId, NotificationType.EVENT_REMINDER).get(0);
        assertThat(restored.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void persistedReminderIsRecoveredAndDeliveredByALaterWorkerRun() throws Exception {
        UUID bookingId = UUID.randomUUID();
        consumer.consume(bookingRecord(UUID.randomUUID(), bookingId, UUID.randomUUID()));
        NotificationIntent reminder = intents.findAllByBusinessIdAndType(
                bookingId, NotificationType.EVENT_REMINDER).get(0);

        deliveryJob.deliverDueAt(reminder.getScheduledAt().plusSeconds(1));

        NotificationIntent delivered = intents.findById(reminder.getId()).orElseThrow();
        assertThat(delivered.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(deliveries.findByIdempotencyKey(delivered.getNotificationKey())).isPresent();
    }

    @Test
    void relevantLifecycleEventsCreateEveryPhaseSixNotificationType() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID attendeeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ConsumerRecord<String, String> bookingRecord = bookingRecord(
                UUID.randomUUID(), bookingId, attendeeId, eventId);
        consumer.consume(bookingRecord);
        Instant newStart = Instant.now().plus(4, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        consume("event-platform.booking.confirmed.v1", "Booking", bookingId, Map.of(
                "bookingId", bookingId.toString(), "attendeeId", attendeeId.toString(),
                "eventId", eventId.toString(), "status", "CONFIRMED"));
        Map<String, Object> payment = new HashMap<>();
        payment.put("paymentId", UUID.randomUUID().toString());
        payment.put("bookingId", bookingId.toString());
        payment.put("attendeeId", attendeeId.toString());
        payment.put("eventId", eventId.toString());
        payment.put("amount", "25.00");
        payment.put("currency", "USD");
        payment.put("eventTitle", "Phase 6 Conference");
        payment.put("eventStartsAt", newStart.toString());
        payment.put("attendeeEmail", "attendee@example.test");
        payment.put("attendeeLocale", "en");
        consume("event-platform.payment.succeeded.v1", "Payment", UUID.fromString((String) payment.get("paymentId")), payment);
        payment.put("paymentId", UUID.randomUUID().toString());
        payment.put("failureReason", "Card declined");
        consume("event-platform.payment.failed.v1", "Payment", UUID.fromString((String) payment.get("paymentId")), payment);
        UUID ticketId = UUID.randomUUID();
        consume("event-platform.ticket.issued.v1", "Ticket", ticketId, Map.ofEntries(
                Map.entry("ticketId", ticketId.toString()),
                Map.entry("bookingId", bookingId.toString()),
                Map.entry("attendeeId", attendeeId.toString()),
                Map.entry("eventId", eventId.toString()),
                Map.entry("ticketTypeId", UUID.randomUUID().toString()),
                Map.entry("eventTitle", "Phase 6 Conference"),
                Map.entry("ticketTypeName", "General Admission"),
                Map.entry("eventStartsAt", newStart.toString()),
                Map.entry("attendeeEmail", "attendee@example.test"),
                Map.entry("attendeeLocale", "en"),
                Map.entry("qrToken", "signed-local-qr-token"),
                Map.entry("issuedAt", Instant.now().toString())));
        consume("event-platform.event.updated.v1", "Event", eventId, Map.of(
                "eventId", eventId.toString(), "startsAt", newStart.toString()));
        consume("event-platform.event.cancelled.v1", "Event", eventId, Map.of(
                "eventId", eventId.toString(), "cancelledAt", Instant.now().toString()));
        UUID refundId = UUID.randomUUID();
        consume("event-platform.refund.succeeded.v1", "Refund", refundId, Map.ofEntries(
                Map.entry("refundId", refundId.toString()),
                Map.entry("paymentId", UUID.randomUUID().toString()),
                Map.entry("bookingId", bookingId.toString()),
                Map.entry("attendeeId", attendeeId.toString()),
                Map.entry("eventId", eventId.toString()),
                Map.entry("amount", "25.00"),
                Map.entry("currency", "USD"),
                Map.entry("eventTitle", "Phase 6 Conference"),
                Map.entry("eventStartsAt", newStart.toString()),
                Map.entry("attendeeEmail", "attendee@example.test"),
                Map.entry("attendeeLocale", "en")));

        assertThat(intents.findAll().stream().map(NotificationIntent::getType).collect(java.util.stream.Collectors.toSet()))
                .containsAll(Set.of(
                        NotificationType.BOOKING_RECEIVED, NotificationType.BOOKING_CONFIRMED,
                        NotificationType.PAYMENT_CONFIRMED, NotificationType.PAYMENT_FAILED,
                        NotificationType.TICKET_ISSUED, NotificationType.EVENT_REMINDER,
                        NotificationType.EVENT_RESCHEDULED, NotificationType.EVENT_CANCELLED,
                        NotificationType.REFUND_CONFIRMED));
    }

    private ConsumerRecord<String, String> bookingRecord(
            UUID messageId, UUID bookingId, UUID attendeeId) throws Exception {
        return bookingRecord(messageId, bookingId, attendeeId, UUID.randomUUID());
    }

    private ConsumerRecord<String, String> bookingRecord(
            UUID messageId, UUID bookingId, UUID attendeeId, UUID eventId) throws Exception {
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Map<String, Object> body = new HashMap<>();
        body.put("bookingId", bookingId.toString());
        body.put("attendeeId", attendeeId.toString());
        body.put("attendeeEmail", "attendee@example.test");
        body.put("attendeePhone", "+201000000000");
        body.put("attendeeLocale", "en");
        body.put("attendeeDisplayName", "Local Attendee");
        body.put("eventId", eventId.toString());
        body.put("ticketTypeId", UUID.randomUUID().toString());
        body.put("inventoryReservationId", UUID.randomUUID().toString());
        body.put("quantity", 1);
        body.put("totalAmount", "25.00");
        body.put("currency", "USD");
        body.put("status", "PAYMENT_PENDING");
        body.put("eventTitle", "Phase 6 Conference");
        body.put("ticketTypeName", "General Admission");
        body.put("eventStartsAt", occurredAt.plus(3, ChronoUnit.DAYS).toString());
        body.put("holdExpiresAt", occurredAt.plus(15, ChronoUnit.MINUTES).toString());
        body.put("occurredAt", occurredAt.toString());
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "event-platform.attendee-lifecycle.v1", 0, 0, bookingId.toString(),
                objectMapper.writeValueAsString(body));
        KafkaEventHeaders.write(record.headers(), new KafkaEventMetadata(
                messageId, BOOKING_CREATED, 1, occurredAt, "phase6-test", null,
                "attendee-service", "Booking", bookingId.toString()));
        return record;
    }

    private void consume(String eventType, String aggregateType, UUID aggregateId, Map<String, ?> body)
            throws Exception {
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "event-platform.test-lifecycle.v1", 0, 0, aggregateId.toString(),
                objectMapper.writeValueAsString(body));
        KafkaEventHeaders.write(record.headers(), new KafkaEventMetadata(
                UUID.randomUUID(), eventType, 1, occurredAt, "phase6-test", null,
                aggregateType.toLowerCase() + "-service", aggregateType, aggregateId.toString()));
        consumer.consume(record);
    }
}
