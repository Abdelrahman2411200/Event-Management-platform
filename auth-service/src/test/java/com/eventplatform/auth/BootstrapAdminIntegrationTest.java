package com.eventplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventplatform.auth.audit.SecurityAuditEventRepository;
import com.eventplatform.auth.user.Role;
import com.eventplatform.auth.user.UserAccount;
import com.eventplatform.auth.user.UserAccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:auth_service_bootstrap;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "platform.auth.bootstrap-admin.enabled=true",
    "platform.auth.bootstrap-admin.email=first-admin@example.com"
})
class BootstrapAdminIntegrationTest {

    private static final String BOOTSTRAP_PASSWORD = "Admin-" + UUID.randomUUID() + "-42";

    @DynamicPropertySource
    static void bootstrapPassword(DynamicPropertyRegistry registry) {
        registry.add("platform.auth.bootstrap-admin.password", () -> BOOTSTRAP_PASSWORD);
    }

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private SecurityAuditEventRepository auditRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void explicitPrivateConfigurationProvisionsTheFirstAdminWithoutPersistingPlaintext() {
        UserAccount admin = userRepository.findByNormalizedEmail("first-admin@example.com").orElseThrow();
        assertThat(admin.getRoles()).contains(Role.ATTENDEE, Role.ADMIN);
        assertThat(admin.getPasswordHash()).doesNotContain(BOOTSTRAP_PASSWORD);
        assertThat(passwordEncoder.matches(BOOTSTRAP_PASSWORD, admin.getPasswordHash())).isTrue();
        assertThat(auditRepository.findAll())
                .anySatisfy(event -> {
                    assertThat(event.getEventType()).isEqualTo("ROLES_CHANGED");
                    assertThat(event.getDetails()).isEqualTo("BOOTSTRAP_ADMIN_PROVISIONED");
                });
    }
}
