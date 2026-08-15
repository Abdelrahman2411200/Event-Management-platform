# ADR 0005: Use short-lived signed access tokens and rotated opaque refresh sessions

- Status: Accepted
- Date: 2026-08-15

## Context

The gateway and independently deployed services need a locally runnable authentication format without making every request synchronously call the auth database. Logout, long-lived sessions, role administration, and stolen-token response still require server-owned state. OAuth provider details must not become the internal authorization model.

## Decision

The auth service issues short-lived RS256 JWT access tokens and publishes the active public key as JWKS. Every accepting boundary validates signature, exact issuer, required audience, time claims, and `typ=access`. Roles are claims derived only from auth-owned account state. The gateway forwards the original bearer token, and downstream services validate it independently.

Long-lived credentials are opaque refresh tokens containing 256 random bits. Only their SHA-256 hashes are persisted in auth-owned refresh-session rows. Each use rotates the token under a database lock; reuse of a rotated token revokes the active family. Logout and role changes revoke refresh state. Already-issued access tokens are not centrally introspected or denylisted and remain valid until their short expiry.

Password credentials use BCrypt. Self-registration grants only `ATTENDEE`; administrative role changes require `ADMIN`. OAuth2 providers are optional adapters through Spring Security client registration, must supply verified email, and cannot automatically link to an existing account.

Local development may generate an ephemeral RSA key. Persistent environments must inject a matching key pair and disable the fallback. Secrets and private keys are never stored in source control.

## Consequences

Most authorized requests avoid an auth-service network call, while refresh/revocation remains auditable and centrally controlled. Logout and role changes have a bounded access-token propagation delay. Key rotation needs an overlap-capable multi-key JWKS procedure before production zero-downtime rotation. Services must implement resource-server validation before their routes are enabled; network placement behind the gateway is not an authorization mechanism.
