package com.eventplatform.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

@Configuration
public class JwtKeyConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtKeyConfiguration.class);

    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    RsaKeyMaterial rsaKeyMaterial(AuthProperties properties, ResourceLoader resourceLoader) {
        AuthProperties.Jwt jwt = properties.getJwt();
        boolean privateConfigured = StringUtils.hasText(jwt.getPrivateKeyLocation());
        boolean publicConfigured = StringUtils.hasText(jwt.getPublicKeyLocation());
        if (privateConfigured != publicConfigured) {
            throw new IllegalStateException("Both JWT private and public key locations must be configured together");
        }

        try {
            RSAPublicKey publicKey;
            RSAPrivateKey privateKey;
            if (privateConfigured) {
                publicKey = readPublicKey(resourceLoader.getResource(jwt.getPublicKeyLocation()));
                privateKey = readPrivateKey(resourceLoader.getResource(jwt.getPrivateKeyLocation()));
                validateKeyPair(publicKey, privateKey);
            } else {
                if (!jwt.isAllowEphemeralKey()) {
                    throw new IllegalStateException("JWT signing keys are required when ephemeral keys are disabled");
                }
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair keyPair = generator.generateKeyPair();
                publicKey = (RSAPublicKey) keyPair.getPublic();
                privateKey = (RSAPrivateKey) keyPair.getPrivate();
                LOGGER.warn("No JWT key locations configured; generated an ephemeral local key. Tokens will be invalid after restart");
            }
            String keyId = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()));
            RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
            return new RsaKeyMaterial(publicKey, privateKey, keyId, jwk);
        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Unable to initialize JWT signing keys", exception);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RsaKeyMaterial keyMaterial) {
        JWKSource<SecurityContext> source = (selector, context) -> selector.select(new JWKSet(keyMaterial.jwk()));
        return new NimbusJwtEncoder(source);
    }

    @Bean
    JwtDecoder jwtDecoder(RsaKeyMaterial keyMaterial, AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyMaterial.publicKey()).build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.getJwt().getIssuer());
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                "aud", claim -> claim != null && claim.contains(properties.getJwt().getAudience()));
        OAuth2TokenValidator<Jwt> tokenType = new JwtClaimValidator<String>("typ", "access"::equals);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience, tokenType));
        return decoder;
    }

    private void validateKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        if (!publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalStateException("JWT public and private keys do not form a key pair");
        }
        if (publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalStateException("JWT RSA keys must be at least 2048 bits");
        }
    }

    private RSAPublicKey readPublicKey(Resource resource)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] encoded = decodePem(resource, "PUBLIC KEY");
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
    }

    private RSAPrivateKey readPrivateKey(Resource resource)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] encoded = decodePem(resource, "PRIVATE KEY");
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private byte[] decodePem(Resource resource, String type) throws IOException {
        String pem = resource.getContentAsString(StandardCharsets.US_ASCII)
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(pem);
    }
}
