package com.eventplatform.notification.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationRuntimeConfiguration {
    @Bean
    Clock notificationClock() {
        return Clock.systemUTC();
    }
}
