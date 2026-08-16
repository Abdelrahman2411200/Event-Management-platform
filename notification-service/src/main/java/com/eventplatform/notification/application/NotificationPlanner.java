package com.eventplatform.notification.application;

import com.eventplatform.notification.config.NotificationProperties;
import com.eventplatform.notification.domain.BookingNotificationProjection;
import com.eventplatform.notification.domain.BookingNotificationProjectionRepository;
import com.eventplatform.notification.domain.NotificationChannel;
import com.eventplatform.notification.domain.NotificationIntent;
import com.eventplatform.notification.domain.NotificationIntentRepository;
import com.eventplatform.notification.domain.NotificationPreference;
import com.eventplatform.notification.domain.NotificationPreferenceRepository;
import com.eventplatform.notification.domain.NotificationRecipient;
import com.eventplatform.notification.domain.NotificationRecipientRepository;
import com.eventplatform.notification.domain.NotificationStatus;
import com.eventplatform.notification.domain.NotificationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPlanner {
    private static final List<NotificationStatus> ACTIVE = List.of(
            NotificationStatus.PENDING, NotificationStatus.RETRY_SCHEDULED);

    private final NotificationRecipientRepository recipients;
    private final NotificationPreferenceRepository preferences;
    private final BookingNotificationProjectionRepository bookings;
    private final NotificationIntentRepository intents;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationPlanner(
            NotificationRecipientRepository recipients,
            NotificationPreferenceRepository preferences,
            BookingNotificationProjectionRepository bookings,
            NotificationIntentRepository intents,
            NotificationProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.recipients = recipients;
        this.preferences = preferences;
        this.bookings = bookings;
        this.intents = intents;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public NotificationRecipient recipient(
            UUID userId, String email, String phone, String locale, String displayName) {
        Instant now = clock.instant();
        NotificationRecipient recipient = recipients.findById(userId)
                .orElseGet(() -> new NotificationRecipient(userId, email, phone, locale, displayName, now));
        recipient.update(email, phone, locale, displayName, now);
        return recipients.save(recipient);
    }

    @Transactional
    public BookingNotificationProjection booking(
            UUID bookingId, UUID attendeeId, UUID eventId, String eventTitle, Instant eventStartsAt) {
        BookingNotificationProjection existing = bookings.findById(bookingId).orElse(null);
        if (existing != null) return existing;
        return bookings.save(new BookingNotificationProjection(
                bookingId, attendeeId, eventId, eventTitle, eventStartsAt, clock.instant()));
    }

    @Transactional
    public void plan(
            UUID sourceMessageId,
            NotificationRecipient recipient,
            UUID businessId,
            NotificationType type,
            Map<String, String> variables,
            Instant scheduledAt) {
        NotificationPreference preference = preference(recipient.getUserId());
        if (type == NotificationType.EVENT_REMINDER && !preference.isRemindersEnabled()) return;
        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            if (type.isMandatory()) {
                throw new IllegalStateException("Mandatory notification has no email destination for user "
                        + recipient.getUserId());
            }
        } else {
            planChannel(sourceMessageId, recipient, businessId, type, NotificationChannel.EMAIL,
                    recipient.getEmail(), variables, scheduledAt);
        }
        if (preference.isSmsEnabled()
                && recipient.getPhoneNumber() != null && !recipient.getPhoneNumber().isBlank()) {
            planChannel(sourceMessageId, recipient, businessId, type, NotificationChannel.SMS,
                    recipient.getPhoneNumber(), variables, scheduledAt);
        }
    }

    @Transactional
    public void cancelRemindersForEvent(UUID eventId) {
        Instant now = clock.instant();
        bookings.findAllByEventId(eventId).forEach(booking -> intents
                .findAllByBusinessIdAndType(booking.getBookingId(), NotificationType.EVENT_REMINDER)
                .forEach(intent -> intent.cancel(now)));
    }

    @Transactional
    public void rescheduleEvent(UUID sourceMessageId, UUID eventId, Instant startsAt) {
        Instant now = clock.instant();
        for (BookingNotificationProjection booking : bookings.findAllByEventId(eventId)) {
            if (!booking.reschedule(startsAt, now)) continue;
            NotificationRecipient recipient = recipients.findById(booking.getAttendeeId()).orElseThrow();
            Map<String, String> variables = Map.of(
                    "eventTitle", booking.getEventTitle(),
                    "eventStartsAt", startsAt.toString(),
                    "bookingId", booking.getBookingId().toString());
            Instant reminderAt = reminderAt(startsAt, now);
            String json = json(variables);
            intents.findAllByBusinessIdAndType(booking.getBookingId(), NotificationType.EVENT_REMINDER)
                    .forEach(intent -> intent.reschedule(reminderAt, json, now));
            plan(sourceMessageId, recipient, booking.getBookingId(), NotificationType.EVENT_RESCHEDULED,
                    variables, now);
        }
    }

    @Transactional
    public void applyPreference(UUID userId, boolean remindersEnabled, boolean smsEnabled) {
        Instant now = clock.instant();
        NotificationPreference preference = preference(userId);
        boolean remindersWereEnabled = preference.isRemindersEnabled();
        preference.update(remindersEnabled, smsEnabled, now);
        if (!remindersEnabled) {
            intents.findAllByUserIdAndTypeAndStatusIn(userId, NotificationType.EVENT_REMINDER, ACTIVE)
                    .forEach(intent -> intent.cancel(now));
        } else if (!remindersWereEnabled) {
            NotificationRecipient recipient = recipients.findById(userId).orElse(null);
            if (recipient == null) return;
            for (BookingNotificationProjection booking : bookings.findAllByAttendeeId(userId)) {
                Map<String, String> variables = Map.of(
                        "eventTitle", booking.getEventTitle(),
                        "eventStartsAt", booking.getEventStartsAt().toString(),
                        "bookingId", booking.getBookingId().toString());
                UUID sourceMessageId = UUID.nameUUIDFromBytes(
                        ("preference-reminder:" + userId + ":" + booking.getBookingId())
                                .getBytes(StandardCharsets.UTF_8));
                plan(sourceMessageId, recipient, booking.getBookingId(), NotificationType.EVENT_REMINDER,
                        variables, reminderAt(booking.getEventStartsAt(), now));
            }
        }
    }

    public Instant reminderAt(Instant eventStartsAt, Instant now) {
        Instant candidate = eventStartsAt.minus(properties.getReminderLead());
        return candidate.isBefore(now) ? now : candidate;
    }

    private NotificationPreference preference(UUID userId) {
        return preferences.findById(userId)
                .orElseGet(() -> preferences.save(new NotificationPreference(userId, clock.instant())));
    }

    private void planChannel(
            UUID sourceMessageId,
            NotificationRecipient recipient,
            UUID businessId,
            NotificationType type,
            NotificationChannel channel,
            String destination,
            Map<String, String> variables,
            Instant scheduledAt) {
        String key = type.name().toLowerCase(java.util.Locale.ROOT) + ":" + businessId + ":" + channel.name();
        NotificationIntent existing = intents.findByNotificationKey(key).orElse(null);
        if (existing != null) {
            if (type == NotificationType.EVENT_REMINDER) {
                existing.reschedule(scheduledAt, json(variables), clock.instant());
            }
            return;
        }
        Instant now = clock.instant();
        intents.save(new NotificationIntent(
                UUID.randomUUID(), key, sourceMessageId, recipient.getUserId(), businessId,
                type, channel, destination, type.name().toLowerCase(java.util.Locale.ROOT),
                recipient.getLocale(), json(variables), scheduledAt,
                properties.getDeliveryMaxAttempts(), now));
    }

    private String json(Map<String, String> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Notification variables could not be serialized", exception);
        }
    }
}
