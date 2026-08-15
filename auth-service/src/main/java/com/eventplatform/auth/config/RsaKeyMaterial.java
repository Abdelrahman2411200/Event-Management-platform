package com.eventplatform.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

public record RsaKeyMaterial(
        RSAPublicKey publicKey,
        RSAPrivateKey privateKey,
        String keyId,
        RSAKey jwk) {

    public Map<String, Object> publicJwkSet() {
        return Map.of("keys", java.util.List.of(jwk.toPublicJWK().toJSONObject()));
    }
}
