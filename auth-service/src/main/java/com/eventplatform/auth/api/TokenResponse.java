package com.eventplatform.auth.api;

import java.time.Instant;

public record TokenResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        String refreshToken,
        Instant refreshExpiresAt,
        UserResponse user) {
}
