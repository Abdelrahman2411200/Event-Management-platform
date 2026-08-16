package com.eventplatform.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform.notification")
public class NotificationProperties {
    private Duration reminderLead = Duration.ofHours(24);
    private int deliveryMaxAttempts = 5;
    private int deliveryBatchSize = 50;

    public Duration getReminderLead() { return reminderLead; }
    public void setReminderLead(Duration reminderLead) { this.reminderLead = reminderLead; }
    public int getDeliveryMaxAttempts() { return deliveryMaxAttempts; }
    public void setDeliveryMaxAttempts(int deliveryMaxAttempts) { this.deliveryMaxAttempts = deliveryMaxAttempts; }
    public int getDeliveryBatchSize() { return deliveryBatchSize; }
    public void setDeliveryBatchSize(int deliveryBatchSize) { this.deliveryBatchSize = deliveryBatchSize; }
}
