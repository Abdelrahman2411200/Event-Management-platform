package com.eventplatform.attendee.api;

import com.eventplatform.contracts.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

public record RequestContext(String correlationId, String traceparent, String bearerToken) {
    public static RequestContext from(HttpServletRequest request) {
        Object attribute = request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE);
        String correlationId = attribute instanceof String value
                ? value : CorrelationIds.resolve(request.getHeader(CorrelationIds.HTTP_HEADER));
        String traceparent = request.getHeader(CorrelationIds.TRACEPARENT_HEADER);
        if (traceparent != null && traceparent.length() > 256) {
            traceparent = null;
        }
        return new RequestContext(correlationId, traceparent, request.getHeader("Authorization"));
    }

    public static RequestContext system(String operation, String reference) {
        String correlation = operation + ":" + reference.replaceAll("[^A-Za-z0-9._:-]", "-");
        return new RequestContext(correlation.substring(0, Math.min(128, correlation.length())), null, null);
    }
}
