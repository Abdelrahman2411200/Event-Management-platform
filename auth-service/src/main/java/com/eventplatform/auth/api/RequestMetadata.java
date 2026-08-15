package com.eventplatform.auth.api;

import com.eventplatform.contracts.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

public record RequestMetadata(String correlationId, String ipAddress, String userAgent) {

    private static final int MAX_IP_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 512;

    public static RequestMetadata from(HttpServletRequest request) {
        Object correlation = request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE);
        String correlationId = correlation instanceof String value
                ? value
                : CorrelationIds.resolve(request.getHeader(CorrelationIds.HTTP_HEADER));
        return new RequestMetadata(
                limit(correlationId, 128),
                limit(request.getRemoteAddr(), MAX_IP_LENGTH),
                limit(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH));
    }

    public RequestMetadata {
        correlationId = limit(correlationId, 128);
        ipAddress = limit(ipAddress, MAX_IP_LENGTH);
        userAgent = limit(userAgent, MAX_USER_AGENT_LENGTH);
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
