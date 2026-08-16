package com.eventplatform.payment.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentRuntimeConfiguration {
    @Bean Clock paymentClock() { return Clock.systemUTC(); }
}
