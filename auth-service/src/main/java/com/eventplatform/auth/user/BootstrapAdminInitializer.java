package com.eventplatform.auth.user;

import com.eventplatform.auth.api.RegistrationRequest;
import com.eventplatform.auth.api.RequestMetadata;
import com.eventplatform.auth.audit.SecurityAuditService;
import com.eventplatform.auth.audit.SecurityEventType;
import com.eventplatform.auth.config.AuthProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "platform.auth.bootstrap-admin", name = "enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final AuthProperties properties;
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;
    private final Validator validator;
    private final Clock clock;

    public BootstrapAdminInitializer(
            AuthProperties properties,
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            SecurityAuditService auditService,
            Validator validator,
            Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.validator = validator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        AuthProperties.BootstrapAdmin bootstrap = properties.getBootstrapAdmin();
        RegistrationRequest request = new RegistrationRequest(bootstrap.getEmail(), bootstrap.getPassword());
        Set<ConstraintViolation<RegistrationRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Bootstrap administrator credentials do not satisfy registration policy");
        }

        String normalizedEmail = bootstrap.getEmail().trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        UserAccount user = userRepository.findByNormalizedEmail(normalizedEmail).orElseGet(() ->
                UserAccount.passwordAccount(
                        bootstrap.getEmail().trim(),
                        normalizedEmail,
                        passwordEncoder.encode(bootstrap.getPassword()),
                        now));
        Set<Role> roles = new LinkedHashSet<>(user.getRoles());
        roles.add(Role.ADMIN);
        user.replaceRoles(roles, now);
        userRepository.saveAndFlush(user);
        auditService.success(
                SecurityEventType.ROLES_CHANGED,
                user.getId(),
                normalizedEmail,
                null,
                new RequestMetadata("bootstrap-admin", "local", null),
                "BOOTSTRAP_ADMIN_PROVISIONED");
    }
}
