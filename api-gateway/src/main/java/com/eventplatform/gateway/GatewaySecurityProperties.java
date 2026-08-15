package com.eventplatform.gateway;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("platform.gateway.security")
public class GatewaySecurityProperties {

    @NotEmpty
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));

    @Valid
    private final Jwt jwt = new Jwt();

    @Valid
    private final RateLimit rateLimit = new RateLimit();

    public List<String> getAllowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = new ArrayList<>(allowedOrigins);
    }

    public Jwt getJwt() {
        return jwt;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class Jwt {

        @NotBlank
        private String issuer = "urn:event-platform:auth";

        @NotBlank
        private String audience = "event-platform-api";

        @NotBlank
        private String jwkSetUri = "http://localhost:8081/api/v1/auth/.well-known/jwks.json";

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

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }
    }

    public static class RateLimit {

        private boolean enabled = true;

        @Valid
        private final Policy registration = new Policy(1, 5);

        @Valid
        private final Policy login = new Policy(1, 10);

        @Valid
        private final Policy refresh = new Policy(1, 10);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Policy getRegistration() {
            return registration;
        }

        public Policy getLogin() {
            return login;
        }

        public Policy getRefresh() {
            return refresh;
        }
    }

    public static class Policy {

        @Min(1)
        private int replenishRate;

        @Min(1)
        private int burstCapacity;

        public Policy() {
        }

        Policy(int replenishRate, int burstCapacity) {
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
        }

        public int getReplenishRate() {
            return replenishRate;
        }

        public void setReplenishRate(int replenishRate) {
            this.replenishRate = replenishRate;
        }

        public int getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(int burstCapacity) {
            this.burstCapacity = burstCapacity;
        }
    }
}
