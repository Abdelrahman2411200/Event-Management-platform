package com.eventplatform.attendee.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(QrProperties.class)
public class AttendeeRuntimeConfiguration {

    @Bean
    Clock platformClock() {
        return Clock.systemUTC();
    }

    @Bean
    RestClient eventInventoryRestClient(
            @Value("${platform.event-service.base-url}") String baseUrl,
            @Value("${platform.event-service.timeout:3s}") Duration timeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
