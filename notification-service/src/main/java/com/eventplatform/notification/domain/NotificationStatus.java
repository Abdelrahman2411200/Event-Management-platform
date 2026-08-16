package com.eventplatform.notification.domain;

public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    RETRY_SCHEDULED,
    DEAD_LETTERED,
    CANCELLED
}
