package com.eventplatform.attendee.api;

import com.eventplatform.contracts.ApiError;
import com.eventplatform.contracts.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AttendeeApiExceptionHandler {
    @ExceptionHandler(AttendeeApiException.class)
    ResponseEntity<ApiError> handle(AttendeeApiException exception, HttpServletRequest request) {
        Object requestCorrelation = request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE);
        String correlationId = requestCorrelation instanceof String value
                ? value : CorrelationIds.resolve(request.getHeader(CorrelationIds.HTTP_HEADER));
        ApiError error = ApiError.withValidation(
                exception.getStatus().value(), exception.getStatus().getReasonPhrase(), exception.getCode(),
                exception.getMessage(), request.getRequestURI(), correlationId, List.of());
        return ResponseEntity.status(exception.getStatus()).body(error);
    }
}
