package com.eventplatform.auth.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCodec {

    private static final int SECRET_BYTES = 32;
    private static final int TOKEN_LENGTH = 80;
    private final SecureRandom secureRandom = new SecureRandom();

    public IssuedRefreshToken issue() {
        UUID sessionId = UUID.randomUUID();
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        String value = sessionId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return new IssuedRefreshToken(sessionId, value, hash(value));
    }

    public Optional<ParsedRefreshToken> parse(String value) {
        if (value == null || value.length() != TOKEN_LENGTH) {
            return Optional.empty();
        }
        int delimiter = value.indexOf('.');
        if (delimiter <= 0 || delimiter == value.length() - 1 || value.indexOf('.', delimiter + 1) >= 0) {
            return Optional.empty();
        }
        try {
            UUID sessionId = UUID.fromString(value.substring(0, delimiter));
            return Optional.of(new ParsedRefreshToken(sessionId, hash(value)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean hashesMatch(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedRefreshToken(UUID sessionId, String value, String hash) {
    }

    public record ParsedRefreshToken(UUID sessionId, String hash) {
    }
}
