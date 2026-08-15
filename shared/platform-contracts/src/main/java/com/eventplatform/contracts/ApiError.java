package com.eventplatform.contracts;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        List<ValidationError> validationDetails) {

    public ApiError {
        validationDetails = validationDetails == null ? List.of() : List.copyOf(validationDetails);
    }

    public static ApiError of(
            int status,
            String error,
            String code,
            String message,
            String path,
            String correlationId) {
        return new ApiError(Instant.now(), status, error, code, message, path, correlationId, List.of());
    }

    public static ApiError withValidation(
            int status,
            String error,
            String code,
            String message,
            String path,
            String correlationId,
            List<ValidationError> validationDetails) {
        return new ApiError(
                Instant.now(), status, error, code, message, path, correlationId, validationDetails);
    }
}
