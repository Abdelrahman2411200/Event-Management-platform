package com.eventplatform.notification.domain;

public enum NotificationType {
    BOOKING_RECEIVED(true),
    BOOKING_CONFIRMED(true),
    PAYMENT_CONFIRMED(true),
    PAYMENT_FAILED(true),
    TICKET_ISSUED(true),
    EVENT_REMINDER(false),
    EVENT_CANCELLED(true),
    EVENT_RESCHEDULED(true),
    REFUND_CONFIRMED(true);

    private final boolean mandatory;

    NotificationType(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
