package com.eventplatform.auth.session;

import com.eventplatform.auth.config.AuthProperties;
import com.eventplatform.auth.config.RsaKeyMaterial;
import com.eventplatform.auth.user.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final RsaKeyMaterial keyMaterial;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtTokenService(
            JwtEncoder encoder,
            RsaKeyMaterial keyMaterial,
            AuthProperties properties,
            Clock clock) {
        this.encoder = encoder;
        this.keyMaterial = keyMaterial;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issue(UserAccount user, UUID sessionId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getJwt().getAccessTokenTtl());
        List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getIssuer())
                .audience(List.of(properties.getJwt().getAudience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("typ", "access")
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .claim("sid", sessionId.toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keyMaterial.keyId())
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, issuedAt, expiresAt);
    }

    public record AccessToken(String value, Instant issuedAt, Instant expiresAt) {
        public long expiresInSeconds() {
            return Math.max(0, expiresAt.getEpochSecond() - issuedAt.getEpochSecond());
        }
    }
}
