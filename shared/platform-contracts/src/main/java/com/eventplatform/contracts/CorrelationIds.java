package com.eventplatform.contracts;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {

    public static final String HTTP_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ATTRIBUTE = CorrelationIds.class.getName() + ".value";
    public static final String MDC_KEY = "correlationId";
    public static final String KAFKA_HEADER = "correlationId";
    public static final String TRACEPARENT_HEADER = "traceparent";

    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private CorrelationIds() {
    }

    public static String resolve(String candidate) {
        if (candidate != null && SAFE_VALUE.matcher(candidate.trim()).matches()) {
            return candidate.trim();
        }
        return UUID.randomUUID().toString();
    }
}
