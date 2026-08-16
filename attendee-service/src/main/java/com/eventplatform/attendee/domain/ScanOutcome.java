package com.eventplatform.attendee.domain;

public enum ScanOutcome {
    VALID,
    CHECKED_IN,
    ALREADY_CHECKED_IN,
    INVALID_TOKEN,
    TAMPERED_TOKEN,
    TICKET_NOT_FOUND,
    WRONG_EVENT,
    TICKET_CANCELLED,
    TICKET_REFUNDED,
    TICKET_NOT_ISSUED,
    ORGANIZER_NOT_OWNER
}
