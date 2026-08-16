package com.eventplatform.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform.kafka.reliability")
public class KafkaReliabilityProperties {
    private int maxRetries = 4;
    private Duration initialBackoff = Duration.ofSeconds(1);
    private double multiplier = 2.0;
    private Duration maxBackoff = Duration.ofSeconds(30);
    private String deadLetterSuffix = ".dlt";

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public Duration getInitialBackoff() { return initialBackoff; }
    public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
    public double getMultiplier() { return multiplier; }
    public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
    public String getDeadLetterSuffix() { return deadLetterSuffix; }
    public void setDeadLetterSuffix(String deadLetterSuffix) { this.deadLetterSuffix = deadLetterSuffix; }
}
