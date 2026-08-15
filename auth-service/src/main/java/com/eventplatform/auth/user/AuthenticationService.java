package com.eventplatform.auth.user;

import com.eventplatform.auth.api.AuthApiException;
import com.eventplatform.auth.api.LoginRequest;
import com.eventplatform.auth.api.RefreshRequest;
import com.eventplatform.auth.api.RegistrationRequest;
import com.eventplatform.auth.api.RequestMetadata;
import com.eventplatform.auth.api.RevocationResponse;
import com.eventplatform.auth.api.TokenResponse;
import com.eventplatform.auth.api.UserResponse;
import com.eventplatform.auth.audit.SecurityAuditService;
import com.eventplatform.auth.audit.SecurityEventType;
import com.eventplatform.auth.config.AuthProperties;
import com.eventplatform.auth.oauth.ExternalIdentity;
import com.eventplatform.auth.oauth.ExternalIdentityRepository;
import com.eventplatform.auth.session.JwtTokenService;
import com.eventplatform.auth.session.RefreshSession;
import com.eventplatform.auth.session.RefreshSessionRepository;
import com.eventplatform.auth.session.RefreshTokenCodec;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserAccountRepository userRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final ExternalIdentityRepository externalIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenCodec refreshTokenCodec;
    private final JwtTokenService jwtTokenService;
    private final SecurityAuditService auditService;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthenticationService(
            UserAccountRepository userRepository,
            RefreshSessionRepository refreshSessionRepository,
            ExternalIdentityRepository externalIdentityRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenCodec refreshTokenCodec,
            JwtTokenService jwtTokenService,
            SecurityAuditService auditService,
            AuthProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.externalIdentityRepository = externalIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenCodec = refreshTokenCodec;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegistrationRequest request, RequestMetadata metadata) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByNormalizedEmail(normalizedEmail)) {
            auditService.failure(
                    SecurityEventType.REGISTRATION_REJECTED,
                    null,
                    normalizedEmail,
                    null,
                    metadata,
                    "DUPLICATE_ACCOUNT");
            throw new AuthApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", "An account already exists for this email");
        }

        Instant now = clock.instant();
        UserAccount user = UserAccount.passwordAccount(
                request.email().trim(), normalizedEmail, passwordEncoder.encode(request.password()), now);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            auditService.failure(
                    SecurityEventType.REGISTRATION_REJECTED,
                    null,
                    normalizedEmail,
                    null,
                    metadata,
                    "DUPLICATE_ACCOUNT");
            throw new AuthApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", "An account already exists for this email");
        }
        auditService.success(
                SecurityEventType.REGISTRATION_SUCCESS,
                user.getId(),
                normalizedEmail,
                null,
                metadata,
                "DEFAULT_ROLE_ATTENDEE");
        return UserResponse.from(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request, RequestMetadata metadata) {
        String normalizedEmail = normalizeEmail(request.email());
        UserAccount user = userRepository.findByNormalizedEmail(normalizedEmail).orElse(null);
        String passwordHash = user == null || user.getPasswordHash() == null
                ? dummyPasswordHash
                : user.getPasswordHash();
        boolean acceptableLength = request.password().getBytes(StandardCharsets.UTF_8).length <= 72;
        String passwordCandidate = acceptableLength ? request.password() : "invalid-overlong-password";
        boolean hashMatches = passwordEncoder.matches(passwordCandidate, passwordHash);
        boolean passwordMatches = acceptableLength && hashMatches;
        if (user == null || user.getPasswordHash() == null || !passwordMatches || !user.isEnabled()) {
            auditService.failure(
                    SecurityEventType.LOGIN_FAILURE,
                    user == null ? null : user.getId(),
                    normalizedEmail,
                    null,
                    metadata,
                    "INVALID_CREDENTIALS");
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        TokenResponse response = createTokenPair(user, UUID.randomUUID(), metadata);
        auditService.success(
                SecurityEventType.LOGIN_SUCCESS,
                user.getId(),
                normalizedEmail,
                parseSessionId(response.refreshToken()),
                metadata,
                "PASSWORD");
        return response;
    }

    @Transactional(noRollbackFor = AuthApiException.class)
    public TokenResponse refresh(RefreshRequest request, RequestMetadata metadata) {
        RefreshTokenCodec.ParsedRefreshToken parsed = refreshTokenCodec.parse(request.refreshToken())
                .orElseThrow(() -> rejectedRefresh(null, null, metadata, "MALFORMED_TOKEN"));
        RefreshSession current = refreshSessionRepository.findByIdForUpdate(parsed.sessionId())
                .orElseThrow(() -> rejectedRefresh(null, parsed.sessionId(), metadata, "UNKNOWN_SESSION"));

        if (!refreshTokenCodec.hashesMatch(current.getTokenHash(), parsed.hash())) {
            throw rejectedRefresh(current.getUserId(), current.getId(), metadata, "TOKEN_HASH_MISMATCH");
        }

        Instant now = clock.instant();
        if (current.isRevoked()) {
            current.recordReuse(now);
            refreshSessionRepository.saveAndFlush(current);
            refreshSessionRepository.revokeActiveFamily(current.getFamilyId(), "REUSE_DETECTED", now);
            auditService.failure(
                    SecurityEventType.REFRESH_REUSE_DETECTED,
                    current.getUserId(),
                    null,
                    current.getId(),
                    metadata,
                    "REFRESH_FAMILY_REVOKED");
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED", "Refresh token reuse was detected");
        }
        if (current.isExpired(now)) {
            current.revoke("EXPIRED", now);
            auditService.failure(
                    SecurityEventType.REFRESH_REJECTED,
                    current.getUserId(),
                    null,
                    current.getId(),
                    metadata,
                    "EXPIRED_TOKEN");
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "Refresh token has expired");
        }

        UserAccount user = userRepository.findById(current.getUserId())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> rejectedRefresh(current.getUserId(), current.getId(), metadata, "USER_DISABLED"));
        RefreshTokenCodec.IssuedRefreshToken replacement = refreshTokenCodec.issue();
        current.rotate(replacement.sessionId(), now);
        RefreshSession next = new RefreshSession(
                replacement.sessionId(),
                user.getId(),
                current.getFamilyId(),
                replacement.hash(),
                now,
                now.plus(properties.getJwt().getRefreshTokenTtl()),
                metadata.ipAddress(),
                metadata.userAgent());
        refreshSessionRepository.save(next);
        JwtTokenService.AccessToken accessToken = jwtTokenService.issue(user, next.getId());
        auditService.success(
                SecurityEventType.REFRESH_ROTATED,
                user.getId(),
                user.getNormalizedEmail(),
                next.getId(),
                metadata,
                "ROTATED");
        return tokenResponse(user, replacement.value(), next.getExpiresAt(), accessToken);
    }

    @Transactional
    public RevocationResponse revokeCurrent(UUID userId, UUID sessionId, RequestMetadata metadata) {
        RefreshSession session = refreshSessionRepository.findByIdForUpdate(sessionId).orElse(null);
        int revoked = 0;
        if (session != null && session.getUserId().equals(userId) && !session.isRevoked()) {
            session.revoke("LOGOUT", clock.instant());
            revoked = 1;
        }
        auditService.success(
                SecurityEventType.SESSION_REVOKED,
                userId,
                null,
                sessionId,
                metadata,
                revoked == 1 ? "LOGOUT" : "ALREADY_INACTIVE");
        return new RevocationResponse(revoked);
    }

    @Transactional
    public RevocationResponse revokeAll(UUID userId, RequestMetadata metadata) {
        int revoked = refreshSessionRepository.revokeActiveForUser(userId, "REVOKE_ALL", clock.instant());
        auditService.success(
                SecurityEventType.ALL_SESSIONS_REVOKED,
                userId,
                null,
                null,
                metadata,
                "REVOKED_COUNT=" + revoked);
        return new RevocationResponse(revoked);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return UserResponse.from(requireUser(userId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse replaceRoles(UUID actorId, UUID targetUserId, Set<Role> roles, RequestMetadata metadata) {
        UserAccount target = requireUser(targetUserId);
        Set<Role> previousRoles = target.getRoles();
        target.replaceRoles(Set.copyOf(roles), clock.instant());
        int revoked = refreshSessionRepository.revokeActiveForUser(targetUserId, "ROLES_CHANGED", clock.instant());
        auditService.success(
                SecurityEventType.ROLES_CHANGED,
                targetUserId,
                target.getNormalizedEmail(),
                null,
                metadata,
                "ACTOR=" + actorId + ";FROM=" + previousRoles + ";TO=" + target.getRoles() + ";SESSIONS=" + revoked);
        return UserResponse.from(target);
    }

    @Transactional
    public TokenResponse oauthLogin(
            String provider,
            String providerSubject,
            String email,
            boolean emailVerified,
            RequestMetadata metadata) {
        if (email == null || !emailVerified) {
            auditService.failure(
                    SecurityEventType.OAUTH_LOGIN_FAILURE,
                    null,
                    null,
                    null,
                    metadata,
                    "VERIFIED_EMAIL_REQUIRED");
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "OAUTH_EMAIL_UNVERIFIED", "OAuth provider must supply a verified email");
        }
        String normalizedEmail = normalizeEmail(email);
        UserAccount user = externalIdentityRepository.findByProviderAndProviderSubject(provider, providerSubject)
                .map(identity -> requireUser(identity.getUserId()))
                .orElseGet(() -> createOAuthAccount(provider, providerSubject, email, normalizedEmail));
        TokenResponse response = createTokenPair(user, UUID.randomUUID(), metadata);
        auditService.success(
                SecurityEventType.OAUTH_LOGIN_SUCCESS,
                user.getId(),
                normalizedEmail,
                parseSessionId(response.refreshToken()),
                metadata,
                "PROVIDER=" + provider);
        return response;
    }

    private UserAccount createOAuthAccount(
            String provider,
            String providerSubject,
            String email,
            String normalizedEmail) {
        if (userRepository.existsByNormalizedEmail(normalizedEmail)) {
            throw new AuthApiException(
                    HttpStatus.CONFLICT,
                    "OAUTH_ACCOUNT_LINK_REQUIRED",
                    "An account already exists; sign in before linking this provider");
        }
        Instant now = clock.instant();
        UserAccount user = userRepository.saveAndFlush(UserAccount.oauthAccount(email.trim(), normalizedEmail, now));
        externalIdentityRepository.save(new ExternalIdentity(user.getId(), provider, providerSubject, now));
        return user;
    }

    private TokenResponse createTokenPair(UserAccount user, UUID familyId, RequestMetadata metadata) {
        Instant now = clock.instant();
        RefreshTokenCodec.IssuedRefreshToken refreshToken = refreshTokenCodec.issue();
        RefreshSession session = new RefreshSession(
                refreshToken.sessionId(),
                user.getId(),
                familyId,
                refreshToken.hash(),
                now,
                now.plus(properties.getJwt().getRefreshTokenTtl()),
                metadata.ipAddress(),
                metadata.userAgent());
        refreshSessionRepository.save(session);
        JwtTokenService.AccessToken accessToken = jwtTokenService.issue(user, session.getId());
        return tokenResponse(user, refreshToken.value(), session.getExpiresAt(), accessToken);
    }

    private TokenResponse tokenResponse(
            UserAccount user,
            String refreshToken,
            Instant refreshExpiresAt,
            JwtTokenService.AccessToken accessToken) {
        return new TokenResponse(
                "Bearer",
                accessToken.value(),
                accessToken.expiresInSeconds(),
                refreshToken,
                refreshExpiresAt,
                UserResponse.from(user));
    }

    private AuthApiException rejectedRefresh(
            UUID userId,
            UUID sessionId,
            RequestMetadata metadata,
            String reason) {
        auditService.failure(
                SecurityEventType.REFRESH_REJECTED,
                userId,
                null,
                sessionId,
                metadata,
                reason);
        return new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid");
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UUID parseSessionId(String refreshToken) {
        return UUID.fromString(refreshToken.substring(0, refreshToken.indexOf('.')));
    }
}
