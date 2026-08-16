package com.eventplatform.attendee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform.security.qr")
public class QrProperties {
    private String signingSecret;
    private boolean allowEphemeralKey = true;
    private String issuer = "urn:event-platform:attendee-service";

    public String getSigningSecret() { return signingSecret; }
    public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }
    public boolean isAllowEphemeralKey() { return allowEphemeralKey; }
    public void setAllowEphemeralKey(boolean allowEphemeralKey) { this.allowEphemeralKey = allowEphemeralKey; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}
