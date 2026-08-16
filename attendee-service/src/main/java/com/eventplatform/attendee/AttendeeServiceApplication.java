package com.eventplatform.attendee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AttendeeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AttendeeServiceApplication.class, args);
    }
}
