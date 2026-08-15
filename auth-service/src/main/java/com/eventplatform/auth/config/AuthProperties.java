package com.eventplatform.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("platform.auth")
public class AuthProperties {

    @Valid
    private final Jwt jwt = new Jwt();

    @Valid
    private final OAuth2 oauth2 = new OAuth2();

    @Valid
    private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();

    @Min(10)
    @Max(14)
    private int bcryptStrength = 12;

    public Jwt getJwt() {
        return jwt;
    }

    public OAuth2 getOauth2() {
        return oauth2;
    }

    public BootstrapAdmin getBootstrapAdmin() {
        return bootstrapAdmin;
    }

    public int getBcryptStrength() {
        return bcryptStrength;
    }

    public void setBcryptStrength(int bcryptStrength) {
        this.bcryptStrength = bcryptStrength;
    }

    public static class Jwt {

        @NotBlank
        private String issuer = "urn:event-platform:auth";

        @NotBlank
        private String audience = "event-platform-api";

        @NotNull
        @DurationMin(seconds = 30)
        @DurationMax(hours = 1)
        private Duration accessTokenTtl = Duration.ofMinutes(10);

        @NotNull
        @DurationMin(minutes = 5)
        @DurationMax(days = 365)
        private Duration refreshTokenTtl = Duration.ofDays(30);

        private String privateKeyLocation;
        private String publicKeyLocation;
        private boolean allowEphemeralKey = true;

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }

        public String getPrivateKeyLocation() {
            return privateKeyLocation;
        }

        public void setPrivateKeyLocation(String privateKeyLocation) {
            this.privateKeyLocation = privateKeyLocation;
        }

        public String getPublicKeyLocation() {
            return publicKeyLocation;
        }

        public void setPublicKeyLocation(String publicKeyLocation) {
            this.publicKeyLocation = publicKeyLocation;
        }

        public boolean isAllowEphemeralKey() {
            return allowEphemeralKey;
        }

        public void setAllowEphemeralKey(boolean allowEphemeralKey) {
            this.allowEphemeralKey = allowEphemeralKey;
        }
    }

    public static class OAuth2 {

        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class BootstrapAdmin {

        private boolean enabled;
        private String email;
        private String password;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
