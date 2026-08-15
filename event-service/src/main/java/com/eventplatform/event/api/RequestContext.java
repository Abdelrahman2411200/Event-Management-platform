package com.eventplatform.event.api;

import com.eventplatform.contracts.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

public record RequestContext(String correlationId, String traceparent) {

    public static RequestContext from(HttpServletRequest request) {
        Object attribute = request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE);
        String correlationId = attribute instanceof String value
                ? value
                : CorrelationIds.resolve(request.getHeader(CorrelationIds.HTTP_HEADER));
        String traceparent = request.getHeader(CorrelationIds.TRACEPARENT_HEADER);
        if (traceparent != null && traceparent.length() > 256) {
            traceparent = null;
        }
        return new RequestContext(correlationId, traceparent);
    }
}
