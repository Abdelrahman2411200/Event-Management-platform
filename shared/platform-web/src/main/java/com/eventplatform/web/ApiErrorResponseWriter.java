package com.eventplatform.web;

import com.eventplatform.contracts.ApiError;
import com.eventplatform.contracts.CorrelationIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String error,
            String code,
            String message) throws IOException {
        String correlationId = correlationId(request);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(CorrelationIds.HTTP_HEADER, correlationId);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiError.of(status, error, code, message, request.getRequestURI(), correlationId));
    }

    static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE);
        return value instanceof String correlationId
                ? correlationId
                : CorrelationIds.resolve(request.getHeader(CorrelationIds.HTTP_HEADER));
    }
}
