package com.eventplatform.auth.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "normalized_email", nullable = false, unique = true, length = 320)
    private String normalizedEmail;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new LinkedHashSet<>();

    protected UserAccount() {
    }

    private UserAccount(
            UUID id,
            String email,
            String normalizedEmail,
            String passwordHash,
            Set<Role> roles,
            Instant now) {
        this.id = id;
        this.email = email;
        this.normalizedEmail = normalizedEmail;
        this.passwordHash = passwordHash;
        this.roles.addAll(roles);
        this.enabled = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserAccount passwordAccount(
            String email,
            String normalizedEmail,
            String passwordHash,
            Instant now) {
        return new UserAccount(
                UUID.randomUUID(), email, normalizedEmail, passwordHash, Set.of(Role.ATTENDEE), now);
    }

    public static UserAccount oauthAccount(String email, String normalizedEmail, Instant now) {
        return new UserAccount(
                UUID.randomUUID(), email, normalizedEmail, null, Set.of(Role.ATTENDEE), now);
    }

    public void replaceRoles(Set<Role> newRoles, Instant now) {
        roles.clear();
        roles.addAll(newRoles);
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
