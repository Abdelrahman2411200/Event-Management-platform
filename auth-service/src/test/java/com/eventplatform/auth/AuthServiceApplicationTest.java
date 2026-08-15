package com.eventplatform.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureObservability
@AutoConfigureMockMvc
@SpringBootTest
class AuthServiceApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void operationalEndpointsEchoCorrelationId() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")
                        .header("X-Correlation-Id", "auth-test-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "auth-test-123"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void prometheusMetricsAreAvailableForScraping() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_info")));
    }

    @Test
    void deniedRequestsUseTheStandardErrorContract() throws Exception {
        mockMvc.perform(get("/api/v1/not-implemented")
                        .header("X-Correlation-Id", "auth-denied-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "auth-denied-123"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/v1/not-implemented"))
                .andExpect(jsonPath("$.correlationId").value("auth-denied-123"))
                .andExpect(jsonPath("$.validationDetails").isArray());
    }

    @Test
    void invalidCorrelationIdIsReplaced() throws Exception {
        String correlationId = mockMvc.perform(get("/actuator/health/readiness")
                        .header("X-Correlation-Id", "unsafe value"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("X-Correlation-Id");

        assertThat(correlationId)
                .isNotEqualTo("unsafe value")
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    }
}
