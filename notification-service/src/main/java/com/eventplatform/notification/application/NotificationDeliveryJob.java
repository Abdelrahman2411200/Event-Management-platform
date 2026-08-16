package com.eventplatform.notification.application;

import com.eventplatform.notification.config.NotificationProperties;
import com.eventplatform.notification.domain.NotificationChannel;
import com.eventplatform.notification.domain.NotificationDeliveryAttempt;
import com.eventplatform.notification.domain.NotificationDeliveryAttemptRepository;
import com.eventplatform.notification.domain.NotificationIntent;
import com.eventplatform.notification.domain.NotificationIntentRepository;
import com.eventplatform.notification.domain.NotificationStatus;
import com.eventplatform.notification.provider.EmailSender;
import com.eventplatform.notification.provider.ProviderMessage;
import com.eventplatform.notification.provider.ProviderReceipt;
import com.eventplatform.notification.provider.SmsSender;
import com.eventplatform.notification.template.RenderedNotification;
import com.eventplatform.notification.template.TemplateCatalog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryJob.class);
    private static final List<NotificationStatus> DUE = List.of(
            NotificationStatus.PENDING, NotificationStatus.RETRY_SCHEDULED);

    private final NotificationIntentRepository intents;
    private final NotificationDeliveryAttemptRepository attempts;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final TemplateCatalog templates;
    private final ObjectMapper objectMapper;
    private final NotificationProperties properties;
    private final Clock clock;
    private final Counter sent;
    private final Counter retries;
    private final Counter deadLetters;

    public NotificationDeliveryJob(
            NotificationIntentRepository intents,
            NotificationDeliveryAttemptRepository attempts,
            EmailSender emailSender,
            SmsSender smsSender,
            TemplateCatalog templates,
            ObjectMapper objectMapper,
            NotificationProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.intents = intents;
        this.attempts = attempts;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.templates = templates;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        this.sent = meterRegistry.counter("platform.notifications.delivery", "result", "sent");
        this.retries = meterRegistry.counter("platform.notifications.delivery", "result", "retry");
        this.deadLetters = meterRegistry.counter("platform.notifications.delivery", "result", "dead-lettered");
    }

    @Scheduled(fixedDelayString = "${platform.notification.delivery-interval:1s}")
    @Transactional
    public void deliverDue() {
        deliverDueAt(clock.instant());
    }

    @Transactional
    public void deliverDueAt(Instant now) {
        for (NotificationIntent intent : intents.findDueForUpdate(
                DUE, now, PageRequest.of(0, properties.getDeliveryBatchSize()))) {
            deliver(intent, now);
        }
    }

    private void deliver(NotificationIntent intent, Instant now) {
        String provider = intent.getChannel() == NotificationChannel.EMAIL
                ? emailSender.name() : smsSender.name();
        int attemptNumber = intent.beginAttempt(now);
        NotificationDeliveryAttempt attempt = attempts.save(new NotificationDeliveryAttempt(
                UUID.randomUUID(), intent.getId(), attemptNumber, provider, now));
        try {
            Map<String, String> variables = objectMapper.readValue(
                    intent.getVariablesJson(), new TypeReference<>() { });
            RenderedNotification rendered = templates.render(
                    intent.getType(), intent.getChannel(), intent.getLocale(), variables);
            ProviderMessage message = new ProviderMessage(
                    intent.getNotificationKey(), intent.getDestination(), rendered.subject(), rendered.body());
            ProviderReceipt receipt = intent.getChannel() == NotificationChannel.EMAIL
                    ? emailSender.send(message) : smsSender.send(message);
            Instant completedAt = clock.instant();
            attempt.sent(receipt.providerMessageId(), completedAt);
            intent.sent(completedAt);
            sent.increment();
        } catch (Exception exception) {
            Instant failedAt = clock.instant();
            attempt.failed(exception.getMessage(), failedAt);
            intent.failed(exception.getMessage(), failedAt);
            if (intent.getStatus() == NotificationStatus.DEAD_LETTERED) {
                deadLetters.increment();
                LOGGER.error("Notification dead-lettered intentId={} sourceMessageId={} type={} channel={} attempts={} error={}",
                        intent.getId(), intent.getSourceMessageId(), intent.getType(), intent.getChannel(), intent.getAttemptCount(),
                        exception.toString());
            } else {
                retries.increment();
                LOGGER.warn("Notification retry scheduled intentId={} sourceMessageId={} type={} channel={} attempts={} error={}",
                        intent.getId(), intent.getSourceMessageId(), intent.getType(), intent.getChannel(), intent.getAttemptCount(),
                        exception.toString());
            }
        }
    }
}
