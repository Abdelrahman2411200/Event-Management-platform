package com.eventplatform.notification.template;

import com.eventplatform.notification.domain.NotificationChannel;
import com.eventplatform.notification.domain.NotificationType;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TemplateCatalog {
    private final TemplateRenderer renderer;
    private final Map<NotificationType, Definition> email = new EnumMap<>(NotificationType.class);
    private final Map<NotificationType, Definition> sms = new EnumMap<>(NotificationType.class);

    public TemplateCatalog(TemplateRenderer renderer) {
        this.renderer = renderer;
        email.put(NotificationType.BOOKING_RECEIVED, definition("Booking received for {{eventTitle}}", "Booking {{bookingId}} was received for {{eventTitle}}."));
        email.put(NotificationType.BOOKING_CONFIRMED, definition("Booking confirmed for {{eventTitle}}", "Your booking {{bookingId}} for {{eventTitle}} is confirmed."));
        email.put(NotificationType.PAYMENT_CONFIRMED, definition("Payment confirmed", "Payment {{amount}} {{currency}} for {{eventTitle}} was confirmed."));
        email.put(NotificationType.PAYMENT_FAILED, definition("Payment failed", "Payment for {{eventTitle}} failed: {{failureReason}}."));
        email.put(NotificationType.TICKET_ISSUED, definition("Your ticket for {{eventTitle}}", "Ticket {{ticketId}} ({{ticketTypeName}}) is ready. QR token: {{qrToken}}"));
        email.put(NotificationType.EVENT_REMINDER, definition("Reminder: {{eventTitle}}", "{{eventTitle}} starts at {{eventStartsAt}}."));
        email.put(NotificationType.EVENT_CANCELLED, definition("Event cancelled: {{eventTitle}}", "{{eventTitle}} has been cancelled."));
        email.put(NotificationType.EVENT_RESCHEDULED, definition("Event rescheduled: {{eventTitle}}", "{{eventTitle}} now starts at {{eventStartsAt}}."));
        email.put(NotificationType.REFUND_CONFIRMED, definition("Refund confirmed", "Your refund of {{amount}} {{currency}} for {{eventTitle}} was confirmed."));
        email.forEach((type, value) -> sms.put(type, definition(null, value.body())));
    }

    public RenderedNotification render(
            NotificationType type, NotificationChannel channel, String locale, Map<String, String> variables) {
        Definition definition = (channel == NotificationChannel.EMAIL ? email : sms).get(type);
        if (definition == null) throw new IllegalArgumentException("No template for " + type + " and " + channel);
        String subject = definition.subject() == null ? null : renderer.render(definition.subject(), variables);
        return new RenderedNotification(subject, renderer.render(definition.body(), variables));
    }

    private Definition definition(String subject, String body) { return new Definition(subject, body); }
    private record Definition(String subject, String body) { }
}
