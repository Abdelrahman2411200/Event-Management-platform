package com.eventplatform.payment.api;

import com.eventplatform.contracts.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

public record RequestContext(String correlationId, String traceparent) {
    public static RequestContext from(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE);
        String correlation = value instanceof String id ? id : CorrelationIds.resolve(request.getHeader(CorrelationIds.HTTP_HEADER));
        String traceparent = request.getHeader(CorrelationIds.TRACEPARENT_HEADER);
        return new RequestContext(correlation, traceparent != null && traceparent.length() <= 256 ? traceparent : null);
    }
    public static RequestContext system(String operation, Object reference) {
        String value = (operation + ":" + reference).replaceAll("[^A-Za-z0-9._:-]", "-");
        return new RequestContext(value.substring(0, Math.min(128, value.length())), null);
    }
}
