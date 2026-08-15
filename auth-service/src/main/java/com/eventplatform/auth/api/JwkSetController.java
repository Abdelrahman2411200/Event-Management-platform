package com.eventplatform.auth.api;

import com.eventplatform.auth.config.RsaKeyMaterial;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/.well-known")
public class JwkSetController {

    private final RsaKeyMaterial keyMaterial;

    public JwkSetController(RsaKeyMaterial keyMaterial) {
        this.keyMaterial = keyMaterial;
    }

    @GetMapping("/jwks.json")
    ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(keyMaterial.publicJwkSet());
    }
}
