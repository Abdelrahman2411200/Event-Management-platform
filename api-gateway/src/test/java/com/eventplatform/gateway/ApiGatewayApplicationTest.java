package com.eventplatform.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.cloud.gateway.server.webflux.enabled=false",
    "management.tracing.enabled=false"
})
class ApiGatewayApplicationTest {

    @Test
    void contextLoads() {
    }
}
