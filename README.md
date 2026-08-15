# Event Management Platform

A local-first, cloud-ready microservices platform built with Spring Boot and React. Phase 2 adds the authentication and API-gateway security boundary; event, venue, attendee, payment, and notification business capabilities remain intentionally unimplemented.

## Technology baseline

- Java 17 bytecode, Spring Boot 3.5, Spring Security, Spring Cloud Gateway, Spring Data JPA
- React 19, TypeScript, Vite, and Tailwind CSS
- PostgreSQL, Redis, and Apache Kafka in KRaft mode
- Flyway database migrations
- Docker Compose, Prometheus, Grafana, Loki, OpenTelemetry, and Jaeger
- GitHub Actions for backend, frontend, and Compose validation

Kubernetes, Helm, Terraform, AWS, external OAuth providers, and cloud-managed services are not required to build, test, or run the repository locally. MinIO remains deferred because no current feature stores objects.

## Repository structure

```text
.
|-- api-gateway/              # Routing, JWT validation, CORS, headers, Redis rate limits
|-- auth-service/             # Accounts, credentials, tokens, roles, OAuth client, audits
|-- event-service/            # Phase 1 boundary skeleton
|-- venue-service/            # Phase 1 boundary skeleton
|-- attendee-service/         # Phase 1 boundary skeleton
|-- payment-service/          # Phase 1 boundary skeleton
|-- notification-service/     # Phase 1 boundary skeleton
|-- frontend/                 # React + TypeScript + Tailwind shell
|-- shared/                   # Narrow, domain-free technical contracts and web baseline
|-- docker/                   # Backend image and PostgreSQL bootstrap
|-- observability/            # Local metrics, logs, and traces
|-- docs/                     # Architecture, security guidance, and ADRs
|-- compose.yaml
|-- Makefile
`-- pom.xml
```

Each service owns its data. Shared modules contain no business entities, repositories, roles, or domain events.

## Prerequisites

- A JDK capable of compiling Java 17 (JDK 17 or newer; the project compiles with `--release 17`)
- Maven 3.9+
- Node.js 20.19+ and npm 11+
- Docker with Docker Compose

Confirm the toolchain with `mvn -version`. Maven reports the Java runtime it actually uses; this is authoritative even if a separate shell cannot initially find `java` on `PATH`.

## Build and test

Run from the repository root:

```bash
mvn clean verify
npm --prefix frontend ci
npm --prefix frontend audit --audit-level=high
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run build
docker compose --profile full-stack config --quiet
```

Focused Phase 2 backend tests:

```bash
mvn -pl auth-service,api-gateway -am test
```

Equivalent root targets include `make build`, `make test`, `make frontend-check`, `make infra-up`, `make infra-down`, `make full-stack`, and `make down`.

## Run locally

Start only PostgreSQL, Redis, and Kafka:

```bash
docker compose up -d --wait
docker compose down
```

Start the complete application and observability stack:

```bash
docker compose --profile full-stack up -d --build --wait
docker compose --profile full-stack --profile observability down
```

The second command preserves volumes. Adding `--volumes` permanently removes Compose-managed local data.

Copy `.env.example` to an uncommitted `.env` only when overriding local defaults. The default auth service generates an ephemeral 2048-bit RSA key so no secret is required locally; all access tokens become invalid when that service restarts. Persistent signing keys, OAuth credentials, and bootstrap-admin credentials must be supplied privately through environment/configuration and must never be committed. See [authentication and gateway security](docs/architecture/security.md).

## Authentication quick start

All browser and public API traffic should enter through `http://localhost:8080`. In PowerShell:

```powershell
$registration = @{
  email = "attendee@example.com"
  password = "local-password-42"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/auth/register `
  -ContentType application/json -Body $registration

$tokens = Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/auth/login `
  -ContentType application/json -Body $registration

Invoke-RestMethod -Uri http://localhost:8080/api/v1/auth/me `
  -Headers @{ Authorization = "Bearer $($tokens.accessToken)" }

$rotated = Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/auth/refresh `
  -ContentType application/json `
  -Body (@{ refreshToken = $tokens.refreshToken } | ConvertTo-Json)

Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/auth/logout `
  -Headers @{ Authorization = "Bearer $($rotated.accessToken)" }
```

Self-registration always creates `ATTENDEE`; client-supplied role fields are ignored by the request contract. `ORGANIZER`, `EVENT_STAFF`, and `ADMIN` are assigned only by the admin-protected role endpoint. An initial admin can be provisioned explicitly with the three `AUTH_BOOTSTRAP_ADMIN_*` settings documented in `.env.example`; it is disabled by default and has no default password.

OAuth2 login is also disabled by default. When enabled, providers use Spring Security's standard client-registration properties, and the callback accepts only a provider-authenticated identity with a verified email. It never automatically links an OAuth identity to an existing password account.

## Phase 2 API surface

| Method and path | Access | Purpose |
| --- | --- | --- |
| `POST /api/v1/auth/register` | Public, rate limited | Create an attendee account |
| `POST /api/v1/auth/login` | Public, rate limited | Issue access and refresh tokens |
| `POST /api/v1/auth/refresh` | Public, rate limited | Rotate a refresh token |
| `GET /api/v1/auth/.well-known/jwks.json` | Public | Publish current RSA public key |
| `GET /api/v1/auth/me` | Bearer token | Return the current account |
| `POST /api/v1/auth/logout` | Bearer token | Revoke the token's refresh session |
| `POST /api/v1/auth/sessions/revoke-all` | Bearer token | Revoke every refresh session for the user |
| `PUT /api/v1/auth/users/{id}/roles` | `ADMIN` | Replace roles and revoke the target's sessions |
| `GET /api/v1/auth/audit-events` | `ADMIN` | Read recent security audit events |

Access tokens are signed RS256 JWTs with issuer, audience, expiry, subject, unique ID, type, roles, and refresh-session ID claims. Refresh tokens are opaque random values; only SHA-256 hashes are stored. Rotation is single-use, and replay revokes the active token family.

## Local endpoints

| Component | Port | Readiness | OpenAPI / UI |
| --- | ---: | --- | --- |
| Frontend | 3000 | `/` | - |
| API gateway | 8080 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| Auth service | 8081 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| Event service | 8082 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| Venue service | 8083 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| Attendee service | 8084 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| Payment service | 8085 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| Notification service | 8086 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| PostgreSQL / Redis / Kafka | 5432 / 6379 / 9092 | Compose health checks | - |
| Prometheus / Grafana / Loki | 9090 / 3001 / 3100 | Component health endpoints | Local UIs where applicable |
| Jaeger / OTLP | 16686 / 4317, 4318 | Container status | Trace UI / ingestion |

## Platform conventions and phase boundary

- Public application endpoints use `/api/v1`.
- API failures share `timestamp`, `status`, `error`, `code`, `message`, `path`, `correlationId`, and optional `validationDetails`.
- `X-Correlation-Id` is validated/generated at the gateway, forwarded downstream, returned, and included in logging context. W3C trace context remains independent.
- The gateway validates JWTs before forwarding protected routes, removes untrusted identity headers, preserves the original bearer token, enforces explicit-origin CORS, applies security headers, and rate-limits authentication endpoints through Redis.
- Domain services still independently deny application routes in Phase 2. Later phases must add resource-server authorization to a service before exposing its domain APIs.
- Kafka remains the only message broker. No Phase 2 authentication action publishes domain events or creates outbox/Saga behavior.

Completed in Phase 2: registration, password login, JWT/JWKS, refresh rotation and revocation, RBAC administration, optional OAuth2 client wiring, security audits, gateway validation and protections, Flyway auth schema, tests, and local configuration. Event, venue, attendee, booking, payment, notification, object-storage, and deployment features remain deferred.

See [system context](docs/architecture/system-context.md), [API conventions](docs/architecture/api-conventions.md), [security architecture](docs/architecture/security.md), and the [ADR index](docs/adr/README.md).
