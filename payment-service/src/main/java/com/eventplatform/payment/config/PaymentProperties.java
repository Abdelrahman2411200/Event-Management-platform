package com.eventplatform.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform.payment")
public record PaymentProperties(String provider, String webhookSecret, Duration processingTimeout, Duration reconciliationDelay) {
    public PaymentProperties {
        provider = provider == null || provider.isBlank() ? "fake" : provider;
        processingTimeout = processingTimeout == null ? Duration.ofSeconds(30) : processingTimeout;
        reconciliationDelay = reconciliationDelay == null ? Duration.ofSeconds(10) : reconciliationDelay;
    }
}
