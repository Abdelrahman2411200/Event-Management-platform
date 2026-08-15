package com.eventplatform.auth.api;

import com.eventplatform.contracts.ApiError;
import com.eventplatform.contracts.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthApiExceptionHandler {

    @ExceptionHandler(AuthApiException.class)
    ResponseEntity<ApiError> handle(AuthApiException exception, HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE);
        String correlationId = value instanceof String correlation
                ? correlation
                : CorrelationIds.resolve(request.getHeader(CorrelationIds.HTTP_HEADER));
        ApiError error = ApiError.of(
                exception.getStatus().value(),
                exception.getStatus().getReasonPhrase(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI(),
                correlationId);
        return ResponseEntity.status(exception.getStatus()).body(error);
    }
}
