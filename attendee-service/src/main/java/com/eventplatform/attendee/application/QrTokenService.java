package com.eventplatform.attendee.application;

import com.eventplatform.attendee.config.QrProperties;
import com.eventplatform.attendee.domain.ScanOutcome;
import com.eventplatform.attendee.domain.Ticket;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QrTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QrTokenService.class);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"ETP-QR\",\"v\":1}";

    private final ObjectMapper objectMapper;
    private final byte[] signingKey;
    private final String issuer;

    public QrTokenService(ObjectMapper objectMapper, QrProperties properties) {
        this.objectMapper = objectMapper;
        this.issuer = properties.getIssuer();
        String configured = properties.getSigningSecret();
        if (configured == null || configured.isBlank()) {
            if (!properties.isAllowEphemeralKey()) {
                throw new IllegalStateException("TICKET_QR_SIGNING_SECRET is required when ephemeral QR keys are disabled");
            }
            this.signingKey = new byte[32];
            new SecureRandom().nextBytes(this.signingKey);
            LOGGER.warn("Using an ephemeral ticket QR signing key; issued QR tokens become invalid after restart");
        } else {
            this.signingKey = configured.getBytes(StandardCharsets.UTF_8);
            if (this.signingKey.length < 32) {
                throw new IllegalStateException("Ticket QR signing secret must contain at least 32 UTF-8 bytes");
            }
        }
    }

    public String issue(Ticket ticket) {
        try {
            String header = ENCODER.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
            QrPayload payload = new QrPayload(
                    issuer, ticket.getId(), ticket.getEventId(), ticket.getTokenVersion(), ticket.getIssuedAt());
            String body = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = header + "." + body;
            return signingInput + "." + ENCODER.encodeToString(sign(signingInput));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Ticket QR payload could not be encoded", exception);
        }
    }

    public QrPayload verify(String token) {
        if (token == null || token.length() > 2048) {
            throw new QrVerificationException(ScanOutcome.INVALID_TOKEN);
        }
        String[] segments = token.split("\\.", -1);
        if (segments.length != 3) {
            throw new QrVerificationException(ScanOutcome.INVALID_TOKEN);
        }
        try {
            byte[] decodedHeader = canonicalDecode(segments[0]);
            byte[] decodedPayload = canonicalDecode(segments[1]);
            byte[] supplied = canonicalDecode(segments[2]);
            byte[] expected = sign(segments[0] + "." + segments[1]);
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw new QrVerificationException(ScanOutcome.TAMPERED_TOKEN);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> header = objectMapper.readValue(decodedHeader, Map.class);
            if (!"HS256".equals(header.get("alg"))
                    || !"ETP-QR".equals(header.get("typ"))
                    || !Integer.valueOf(1).equals(header.get("v"))) {
                throw new QrVerificationException(ScanOutcome.INVALID_TOKEN);
            }
            QrPayload payload = objectMapper.readValue(decodedPayload, QrPayload.class);
            if (!issuer.equals(payload.iss()) || payload.ticketId() == null || payload.eventId() == null
                    || payload.tokenVersion() < 1 || payload.issuedAt() == null) {
                throw new QrVerificationException(ScanOutcome.INVALID_TOKEN);
            }
            return payload;
        } catch (QrVerificationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new QrVerificationException(ScanOutcome.INVALID_TOKEN);
        }
    }

    private byte[] canonicalDecode(String segment) {
        byte[] decoded = DECODER.decode(segment);
        if (!ENCODER.encodeToString(decoded).equals(segment)) {
            throw new QrVerificationException(ScanOutcome.TAMPERED_TOKEN);
        }
        return decoded;
    }

    public String fingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    public record QrPayload(String iss, UUID ticketId, UUID eventId, int tokenVersion, Instant issuedAt) {
    }

    public static class QrVerificationException extends RuntimeException {
        private final ScanOutcome outcome;

        QrVerificationException(ScanOutcome outcome) {
            this.outcome = outcome;
        }

        public ScanOutcome outcome() { return outcome; }
    }
}
