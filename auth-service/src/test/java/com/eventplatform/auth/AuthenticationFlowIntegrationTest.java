package com.eventplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventplatform.auth.audit.SecurityAuditEventRepository;
import com.eventplatform.auth.config.AuthProperties;
import com.eventplatform.auth.config.RsaKeyMaterial;
import com.eventplatform.auth.oauth.ExternalIdentityRepository;
import com.eventplatform.auth.session.RefreshSession;
import com.eventplatform.auth.session.RefreshSessionRepository;
import com.eventplatform.auth.user.Role;
import com.eventplatform.auth.user.UserAccount;
import com.eventplatform.auth.user.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AuthenticationFlowIntegrationTest {

    private static final String PASSWORD = "correct-horse-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private ExternalIdentityRepository externalIdentityRepository;

    @Autowired
    private SecurityAuditEventRepository auditRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private RsaKeyMaterial keyMaterial;

    @Autowired
    private AuthProperties properties;

    @BeforeEach
    void clearState() {
        auditRepository.deleteAll();
        refreshSessionRepository.deleteAll();
        externalIdentityRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registrationValidatesInputHashesPasswordAndCannotSelfAssignPrivilegedRoles() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"weak"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationDetails.length()").value(2));

        JsonNode registered = register("Person@Example.com", "ADMIN");
        assertThat(registered.path("roles").findValuesAsText("")).isEmpty();
        assertThat(registered.path("roles").toString()).contains("ATTENDEE").doesNotContain("ADMIN");

        UserAccount persisted = userRepository.findByNormalizedEmail("person@example.com").orElseThrow();
        assertThat(persisted.getRoles()).containsExactly(Role.ATTENDEE);
        assertThat(persisted.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, persisted.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateRegistrationUsesTheStandardConflictContract() throws Exception {
        register("duplicate@example.com", null);

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Correlation-Id", "duplicate-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"DUPLICATE@example.com","password":"correct-horse-42"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Correlation-Id", "duplicate-test"))
                .andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"))
                .andExpect(jsonPath("$.correlationId").value("duplicate-test"));
    }

    @Test
    void loginReturnsVerifiableAccessAndOpaqueRefreshTokensWhileFailureIsAudited() throws Exception {
        JsonNode registered = register("login@example.com", null);
        JsonNode tokens = login("login@example.com", PASSWORD, status().isOk());

        assertThat(tokens.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.path("accessToken").asText()).isNotBlank();
        assertThat(tokens.path("refreshToken").asText()).contains(".");
        assertThat(tokens.path("expiresIn").asLong()).isPositive();
        org.springframework.security.oauth2.jwt.Jwt decoded = jwtDecoder.decode(tokens.path("accessToken").asText());
        assertThat(decoded.getSubject()).isEqualTo(registered.path("id").asText());
        assertThat(decoded.getAudience()).contains(properties.getJwt().getAudience());
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ATTENDEE");
        assertThat(decoded.getClaimAsString("sid")).isNotBlank();

        login("login@example.com", "wrong-password-42", status().isUnauthorized());
        assertThat(auditRepository.findAll())
                .extracting(event -> event.getEventType() + ":" + event.getOutcome())
                .contains("LOGIN_SUCCESS:SUCCESS", "LOGIN_FAILURE:FAILURE");
    }

    @Test
    void refreshRotationRejectsReplayAndRevokesTheReplacementFamily() throws Exception {
        register("rotation@example.com", null);
        JsonNode login = login("rotation@example.com", PASSWORD, status().isOk());
        String original = login.path("refreshToken").asText();

        JsonNode rotated = refresh(original, status().isOk());
        String replacement = rotated.path("refreshToken").asText();
        assertThat(replacement).isNotEqualTo(original);

        refresh(original, status().isUnauthorized())
                .path("code").asText();
        JsonNode rejectedReplacement = refresh(replacement, status().isUnauthorized());
        assertThat(rejectedReplacement.path("code").asText())
                .isIn("REFRESH_TOKEN_REUSED", "INVALID_REFRESH_TOKEN");
        assertThat(auditRepository.findAll())
                .extracting(event -> event.getEventType())
                .contains("REFRESH_ROTATED", "REFRESH_REUSE_DETECTED");
    }

    @Test
    void expiredRefreshTokenIsRejectedAndMarkedInactive() throws Exception {
        register("expired-refresh@example.com", null);
        JsonNode login = login("expired-refresh@example.com", PASSWORD, status().isOk());
        String refreshToken = login.path("refreshToken").asText();
        UUID sessionId = sessionId(refreshToken);
        RefreshSession session = refreshSessionRepository.findById(sessionId).orElseThrow();
        ReflectionTestUtils.setField(session, "expiresAt", Instant.now().minusSeconds(1));
        refreshSessionRepository.saveAndFlush(session);

        JsonNode error = refresh(refreshToken, status().isUnauthorized());
        assertThat(error.path("code").asText()).isEqualTo("REFRESH_TOKEN_EXPIRED");
        assertThat(refreshSessionRepository.findById(sessionId).orElseThrow().getRevokeReason()).isEqualTo("EXPIRED");
    }

    @Test
    void logoutAndRevokeAllInvalidateRefreshSessions() throws Exception {
        register("revoke@example.com", null);
        JsonNode first = login("revoke@example.com", PASSWORD, status().isOk());
        JsonNode second = login("revoke@example.com", PASSWORD, status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + first.path("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedSessions").value(1));
        refresh(first.path("refreshToken").asText(), status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/sessions/revoke-all")
                        .header("Authorization", "Bearer " + second.path("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedSessions").value(1));
        refresh(second.path("refreshToken").asText(), status().isUnauthorized());
    }

    @Test
    void roleChangesAreAdminOnlyAndRevokeTargetSessions() throws Exception {
        JsonNode target = register("roles@example.com", null);
        JsonNode attendeeTokens = login("roles@example.com", PASSWORD, status().isOk());

        mockMvc.perform(put("/api/v1/auth/users/{id}/roles", target.path("id").asText())
                        .header("Authorization", "Bearer " + attendeeTokens.path("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ORGANIZER\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        UUID adminId = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/auth/users/{id}/roles", target.path("id").asText())
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(adminId.toString()).claim("sid", UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ORGANIZER\",\"EVENT_STAFF\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles").isArray());

        assertThat(userRepository.findById(UUID.fromString(target.path("id").asText())).orElseThrow().getRoles())
                .containsExactlyInAnyOrder(Role.ORGANIZER, Role.EVENT_STAFF);
        refresh(attendeeTokens.path("refreshToken").asText(), status().isUnauthorized());
        assertThat(auditRepository.findAll()).extracting(event -> event.getEventType()).contains("ROLES_CHANGED");
    }

    @Test
    void invalidAndExpiredAccessTokensUseTheStandardUnauthorizedContract() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer not-a-jwt")
                        .header("X-Correlation-Id", "invalid-access"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"))
                .andExpect(jsonPath("$.correlationId").value("invalid-access"));

        String expired = expiredAccessToken();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

        String wrongType = signedAccessToken(Instant.now().plusSeconds(600), "refresh");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + wrongType))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void overlongLoginPasswordsAndMalformedJsonAreSafelyRejected() throws Exception {
        String maximumPassword = "A1" + "x".repeat(70);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", "password-limit@example.com",
                                "password", maximumPassword))))
                .andExpect(status().isCreated());

        login("password-limit@example.com", maximumPassword + "extra", status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"password\":\"do-not-echo-42\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body or parameters are malformed"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("do-not-echo-42"));
    }

    @Test
    void jwksEndpointPublishesNoPrivateKeyMaterial() throws Exception {
        String body = mockMvc.perform(get("/api/v1/auth/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").exists())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("PRIVATE KEY");
    }

    private JsonNode register(String email, String requestedRole) throws Exception {
        String roleField = requestedRole == null ? "" : ",\"roles\":[\"" + requestedRole + "\"]";
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"" + roleField + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode login(String email, String password, org.springframework.test.web.servlet.ResultMatcher statusMatcher)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(statusMatcher)
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode refresh(String refreshToken, org.springframework.test.web.servlet.ResultMatcher statusMatcher)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(statusMatcher)
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private UUID sessionId(String refreshToken) {
        return UUID.fromString(refreshToken.substring(0, refreshToken.indexOf('.')));
    }

    private String expiredAccessToken() {
        return signedAccessToken(Instant.now().minusSeconds(60), "access");
    }

    private String signedAccessToken(Instant expiresAt, String tokenType) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getIssuer())
                .audience(List.of(properties.getJwt().getAudience()))
                .issuedAt(expiresAt.isBefore(now) ? now.minusSeconds(120) : now)
                .expiresAt(expiresAt)
                .subject(UUID.randomUUID().toString())
                .claim("typ", tokenType)
                .claim("roles", List.of("ATTENDEE"))
                .claim("sid", UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyMaterial.keyId()).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
