package com.eventplatform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.eventplatform.notification.application.NotificationDeliveryJob;
import com.eventplatform.notification.application.NotificationPlanner;
import com.eventplatform.notification.domain.NotificationDeliveryAttemptRepository;
import com.eventplatform.notification.domain.NotificationIntent;
import com.eventplatform.notification.domain.NotificationIntentRepository;
import com.eventplatform.notification.domain.NotificationPreferenceRepository;
import com.eventplatform.notification.domain.NotificationRecipient;
import com.eventplatform.notification.domain.NotificationRecipientRepository;
import com.eventplatform.notification.domain.NotificationStatus;
import com.eventplatform.notification.domain.NotificationType;
import com.eventplatform.notification.provider.EmailSender;
import com.eventplatform.notification.provider.ProviderReceipt;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest
class NotificationDeliveryFailureIntegrationTest {
    @MockitoBean private EmailSender emailSender;
    @Autowired private NotificationPlanner planner;
    @Autowired private NotificationDeliveryJob deliveryJob;
    @Autowired private NotificationIntentRepository intents;
    @Autowired private NotificationDeliveryAttemptRepository attempts;
    @Autowired private NotificationRecipientRepository recipients;
    @Autowired private NotificationPreferenceRepository preferences;

    @BeforeEach
    void clean() {
        attempts.deleteAll();
        intents.deleteAll();
        recipients.deleteAll();
        preferences.deleteAll();
        when(emailSender.name()).thenReturn("outage-test-email");
    }

    @Test
    void providerOutageSchedulesRetryAndThenRecoversWithoutANewIntent() {
        NotificationIntent intent = planMandatory();
        when(emailSender.send(any()))
                .thenThrow(new IllegalStateException("provider unavailable"))
                .thenReturn(new ProviderReceipt("provider-message-1"));

        deliveryJob.deliverDueAt(Instant.now().plusSeconds(1));
        deliveryJob.deliverDueAt(Instant.now().plusSeconds(600));

        NotificationIntent recovered = intents.findById(intent.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(recovered.getAttemptCount()).isEqualTo(2);
        assertThat(attempts.findAllByIntentIdOrderByAttemptNumber(intent.getId())).hasSize(2);
    }

    @Test
    void exhaustedProviderRetriesAreDeadLettered() {
        NotificationIntent intent = planMandatory();
        when(emailSender.send(any())).thenThrow(new IllegalStateException("provider unavailable"));

        deliveryJob.deliverDueAt(Instant.now().plusSeconds(1));
        deliveryJob.deliverDueAt(Instant.now().plusSeconds(600));
        deliveryJob.deliverDueAt(Instant.now().plusSeconds(1200));

        NotificationIntent deadLettered = intents.findById(intent.getId()).orElseThrow();
        assertThat(deadLettered.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
        assertThat(deadLettered.getAttemptCount()).isEqualTo(3);
        assertThat(attempts.findAllByIntentIdOrderByAttemptNumber(intent.getId())).hasSize(3);
    }

    private NotificationIntent planMandatory() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        NotificationRecipient recipient = planner.recipient(
                userId, "outage@example.test", null, "en", "Outage Test");
        planner.plan(UUID.randomUUID(), recipient, bookingId, NotificationType.BOOKING_RECEIVED,
                Map.of(
                        "bookingId", bookingId.toString(),
                        "eventTitle", "Provider Outage Test",
                        "eventStartsAt", Instant.now().plusSeconds(3600).toString()),
                Instant.now());
        return intents.findAllByBusinessIdAndType(bookingId, NotificationType.BOOKING_RECEIVED).get(0);
    }
}
