package com.eventplatform.event.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventRuntimeConfiguration {
    @Bean
    Clock eventClock() {
        return Clock.systemUTC();
    }
}
