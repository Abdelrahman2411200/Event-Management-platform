# Authentication and gateway security

## Trust boundaries

The API gateway is the public HTTP edge. It authenticates protected `/api/v1/**` requests, enforces cross-origin and abuse controls, removes caller-supplied identity headers, and forwards the original bearer token. It does not manufacture trusted `X-User-*` headers.

The auth service is the authority for accounts, credentials, roles, refresh sessions, external identities, and security audit records. It also validates JWTs on its own protected endpoints. Later domain services must validate the bearer token and enforce their own authorization rules before any domain route is enabled; being behind the gateway is not sufficient authorization.

Actuator health/info/metrics and OpenAPI paths are public for local orchestration and observability. Production ingress must restrict operational endpoints to the appropriate network or monitoring identity.

## Access tokens and signing keys

Access tokens are RS256 JWTs. Required validation includes the RSA signature, expiry/not-before rules, exact issuer, required audience, and `typ=access`. Issued claims are:

| Claim | Meaning |
| --- | --- |
| `iss` / `aud` | Configured trust domain and API audience |
| `sub` | Auth-owned user UUID |
| `jti` | Unique access-token identifier |
| `typ` | Always `access` |
| `email` | Current login email |
| `roles` | `ATTENDEE`, `ORGANIZER`, `EVENT_STAFF`, and/or `ADMIN` |
| `sid` | Refresh-session UUID associated with the access token |
| `iat` / `exp` | UTC issue and expiry times |

The default access lifetime is 10 minutes and configuration validation permits 30 seconds through 1 hour. The public key is exposed at `/api/v1/auth/.well-known/jwks.json`; private RSA values are never published.

Local startup without key locations generates an ephemeral 2048-bit pair and logs that tokens will not survive restart. For persistent environments, provide matching PKCS#8 private and X.509 public PEM files, set both resource locations, and disable ephemeral keys:

```text
JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/jwt-private.pem
JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/jwt-public.pem
JWT_ALLOW_EPHEMERAL_KEY=false
```

The service rejects a missing half of the pair, a mismatched pair, or RSA keys below 2048 bits. Key files belong in a secret store or uncommitted `.local/` directory, never in the image or repository. A Compose override can mount local files read-only at the paths above. Rotate keys with an overlap window only after multi-key JWKS support is introduced; Phase 2 publishes one active key, so changing it invalidates existing access tokens.

The gateway obtains verification keys from `AUTH_JWK_SET_URI` and separately validates `JWT_ISSUER` and `JWT_AUDIENCE`. In deployed environments the JWKS URI must use a trusted TLS/private-network path. If JWT verification infrastructure is unavailable, protected requests fail closed with `503 TOKEN_VALIDATION_UNAVAILABLE`.

## Password accounts

- Emails are validated, trimmed, normalized with locale-independent lowercase, and protected by a unique database constraint.
- Passwords must contain a letter and digit and contain 12 through 72 UTF-8 bytes.
- Password hashes use Spring Security BCrypt with configurable work factor 10 through 14 (default 12).
- Login always performs a BCrypt comparison, including unknown users and rejected overlong candidates, and returns one generic credential error.
- Passwords and raw tokens are never written to application logs, error bodies, or audit records.

Self-registration always assigns exactly `ATTENDEE`. Extra JSON fields cannot self-assign a role. `ADMIN` alone may replace another account's role set through the protected endpoint; the change revokes all of the target account's refresh sessions.

An initial admin is an explicit operational action. Set `AUTH_BOOTSTRAP_ADMIN_ENABLED=true` together with a private, policy-compliant email/password. There are no defaults. If the email already exists, the configured operation promotes that account and does not reset its password. Disable the bootstrap setting after provisioning.

## Refresh sessions and logout

Refresh tokens are opaque values composed of a session UUID and 256 bits of secure random material. The database stores only a SHA-256 token hash and metadata; comparisons use constant-time digest comparison. The default token lifetime is 30 days, with a validated configuration range of 5 minutes through 365 days.

Every successful refresh uses a pessimistic row lock, revokes the presented token as `ROTATED`, and creates a new token in the same family. A second use of a rotated token records replay and revokes every currently active session in that family. Concurrent refreshes therefore converge safely. Expired, malformed, unknown, mismatched, disabled-user, and revoked sessions cannot issue tokens.

`POST /logout` revokes the refresh session referenced by the authenticated access token. `POST /sessions/revoke-all` revokes every refresh session owned by that user. Stateless access tokens already issued remain valid until their short expiry; role changes and logout do not create an access-token denylist in Phase 2.

## OAuth2 client boundary

OAuth2 login is disabled unless `OAUTH2_ENABLED=true`. Provider registrations use Spring Security's standard `spring.security.oauth2.client.registration.*` and `provider.*` properties, so credentials remain external configuration and providers can be added without auth-domain changes.

The success boundary requires an authenticated provider subject, email, and a true `email_verified` or `verified_email` claim. A new OAuth account receives only `ATTENDEE`. If that normalized email already belongs to a password or other account, automatic linking is refused with `OAUTH_ACCOUNT_LINK_REQUIRED`; an authenticated linking workflow is deferred. Provider SDK types do not enter the account domain.

For local JVM experiments, supply registration properties through environment variables or an uncommitted profile, for example the standard `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_<NAME>_CLIENT_ID` and `_CLIENT_SECRET`. Compose users should add those private values in an uncommitted override file. No external provider is needed by automated tests.

## Gateway controls

- Public allowlist: registration, login, refresh, JWKS, OAuth callbacks, published-event list/detail, active event categories, health/metrics, and API docs. Other `/api/v1/**` routes require a validated bearer token; everything else is denied.
- CORS: exact configured origins only (default `http://localhost:3000`), explicit methods and headers, credentials enabled, and a standard `403 CORS_ORIGIN_DENIED` body for rejected origins. Wildcard origins are not used.
- Identity spoofing: inbound `X-User-*` and `X-Authenticated-User-*` headers are removed. `Authorization`, `X-Correlation-Id`, `Idempotency-Key`, and trace propagation remain available to downstream boundaries.
- Security headers: `Referrer-Policy: no-referrer`, restrictive camera/microphone/geolocation permissions, MIME sniffing disabled, and framing denied. TLS/HSTS belongs at HTTPS ingress; HSTS is intentionally not asserted by the local HTTP gateway.
- Authentication abuse control: registration, login, and refresh use separate Redis token buckets keyed by the direct remote address. Defaults are 1 token/second with bursts of 5, 10, and 10 respectively. A Redis failure fails these endpoints closed with `503 RATE_LIMIT_UNAVAILABLE`; protected non-auth routes do not depend on this filter.

Do not trust arbitrary forwarded-address headers. When deploying behind a reverse proxy, configure Spring's forwarded-header support only together with an ingress that strips caller-supplied forwarding headers and a documented trusted-proxy boundary.

## Audit and error handling

The auth database records registration success/rejection, password and OAuth login outcomes, refresh rotation/rejection/replay, current/all-session revocation, and role changes. Each record may contain user/session identifiers, a bounded subject identifier, correlation ID, direct remote address, bounded user agent, outcome, reason code, and UTC time. Application logs contain only event type, outcome, user ID, and correlation ID.

Only `ADMIN` may query the most recent audit events; the requested limit is bounded to 1 through 200. Audit data is auth-owned and is not queried by another service.

All controlled gateway/auth failures use the shared API error contract. JSON parsing details, stack traces, token material, SQL, provider payloads, and secrets are not returned. `401` bearer failures include `WWW-Authenticate: Bearer`, and correlation IDs are returned on success and failure paths.

## Payment and webhook boundary

Payment-service binds payment creation to the booking attendee snapshot and restricts refund reads/actions to the attendee, owning organizer, or admin policy. The gateway and payment-service leave `/api/v1/payments/webhooks/{provider}` without bearer authentication because a provider cannot obtain a user JWT; payment-service instead requires the adapter's cryptographic webhook signature and deduplicates provider event IDs. A browser callback is never proof of payment. Raw card/CVV data, payment method tokens, provider secrets, and raw webhook payloads are not persisted or logged.

## Configuration reference

| Setting | Default | Purpose |
| --- | --- | --- |
| `JWT_ISSUER` | `urn:event-platform:auth` | Exact token issuer |
| `JWT_AUDIENCE` | `event-platform-api` | Required API audience |
| `JWT_ACCESS_TOKEN_TTL` | `10m` | Access-token lifetime |
| `JWT_REFRESH_TOKEN_TTL` | `30d` | Per-refresh-token lifetime |
| `JWT_PRIVATE_KEY_LOCATION` / `JWT_PUBLIC_KEY_LOCATION` | empty | Spring resource paths for a persistent RSA pair |
| `JWT_ALLOW_EPHEMERAL_KEY` | `true` | Local-only key generation fallback |
| `AUTH_BCRYPT_STRENGTH` | `12` | BCrypt work factor |
| `OAUTH2_ENABLED` | `false` | Enable configured OAuth2 login providers |
| `AUTH_BOOTSTRAP_ADMIN_*` | disabled/empty | Explicit first-admin provisioning |
| `GATEWAY_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated exact browser origins |
| `GATEWAY_RATE_LIMIT_ENABLED` | `true` | Authentication endpoint rate limiting |
| `*_RATE_REPLENISH` / `*_RATE_BURST` | see above | Per-flow Redis token-bucket policy |

Database credentials, signing material, OAuth secrets, and bootstrap credentials must be injected privately. Values in Compose are local-only development defaults and must not be reused outside a developer machine.
