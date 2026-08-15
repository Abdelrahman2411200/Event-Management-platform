package com.eventplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "platform.auth.oauth2.enabled=true",
    "spring.datasource.url=jdbc:h2:mem:auth_service_oauth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.security.oauth2.client.registration.test.client-id=test-client",
    "spring.security.oauth2.client.registration.test.authorization-grant-type=authorization_code",
    "spring.security.oauth2.client.registration.test.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
    "spring.security.oauth2.client.registration.test.scope=email",
    "spring.security.oauth2.client.provider.test.authorization-uri=https://provider.example/authorize",
    "spring.security.oauth2.client.provider.test.token-uri=https://provider.example/token",
    "spring.security.oauth2.client.provider.test.user-info-uri=https://provider.example/userinfo",
    "spring.security.oauth2.client.provider.test.user-name-attribute=sub"
})
class OAuth2ConfigurationIntegrationTest {

    private static final String CLIENT_SECRET = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void oauthSecret(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.registration.test.client-secret", () -> CLIENT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configuredProviderStartsTheAuthorizationCodeFlow() throws Exception {
        String location = mockMvc.perform(get("/oauth2/authorization/test"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        assertThat(location)
                .startsWith("https://provider.example/authorize?")
                .contains("client_id=test-client")
                .contains("response_type=code")
                .doesNotContain("client_secret");
    }
}
