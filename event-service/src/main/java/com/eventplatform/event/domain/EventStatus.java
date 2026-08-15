package com.eventplatform.event.domain;

import java.util.EnumSet;
import java.util.Set;

public enum EventStatus {
    DRAFT,
    PUBLISHED,
    SALES_OPEN,
    SOLD_OUT,
    CANCELLED,
    COMPLETED,
    ARCHIVED;

    private static final Set<EventStatus> PUBLIC_STATUSES = EnumSet.of(
            PUBLISHED, SALES_OPEN, SOLD_OUT, CANCELLED, COMPLETED);

    public boolean isPublic() {
        return PUBLIC_STATUSES.contains(this);
    }

    public static Set<EventStatus> publicStatuses() {
        return Set.copyOf(PUBLIC_STATUSES);
    }
}
