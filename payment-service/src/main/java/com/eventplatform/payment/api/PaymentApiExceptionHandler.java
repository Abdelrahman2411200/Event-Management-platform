package com.eventplatform.payment.api;

import com.eventplatform.contracts.ApiError;
import com.eventplatform.contracts.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentApiExceptionHandler {
    @ExceptionHandler(PaymentApiException.class)
    ResponseEntity<ApiError> handle(PaymentApiException exception, HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE);
        String correlation = value instanceof String id ? id : CorrelationIds.resolve(request.getHeader(CorrelationIds.HTTP_HEADER));
        return ResponseEntity.status(exception.getStatus()).body(ApiError.withValidation(
                exception.getStatus().value(), exception.getStatus().getReasonPhrase(), exception.getCode(),
                exception.getMessage(), request.getRequestURI(), correlation, List.of()));
    }
}
