package com.eventplatform.notification.provider;

public record ProviderMessage(
        String idempotencyKey,
        String destination,
        String subject,
        String body) {
}
