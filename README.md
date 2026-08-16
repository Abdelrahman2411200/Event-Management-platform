# Event Management Platform

A local-first, cloud-ready microservices platform built with Spring Boot and React. Phase 4 adds attendee profiles, durable idempotent booking commands, inventory-backed ticket holds, zero-price ticket issuance, signed QR tickets, and concurrency-safe check-in. Provider-backed payment, notification delivery, and waitlisting remain intentionally deferred.

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
|-- event-service/            # Events, categories, ticket products, inventory, outbox
|-- venue-service/            # Venues, rooms, location metadata, availability
|-- attendee-service/         # Profiles, bookings, holds, tickets, QR scans, outbox
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

For the installed Temurin 25 distribution on Windows, validate Phase 4 in the current PowerShell session with:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
mvn clean verify
```

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

Focused Phase 4 backend tests under the current JDK:

```bash
mvn -pl event-service,attendee-service,api-gateway -am test
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

Phase 4 local defaults use 15-minute event inventory holds, a 30-second attendee expiry sweep, and Kafka topics for event and attendee lifecycle records. Local QR signing may use an ephemeral key; inject at least 32 private bytes and disable ephemeral fallback anywhere tickets must survive a restart. These settings are documented in `.env.example`; no cloud account or external provider is required.

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

## Phase 3 API surface

| Boundary | Public reads | Authenticated management |
| --- | --- | --- |
| Categories | `GET /api/v1/event-categories` | Admin create/update/archive |
| Events | `GET /api/v1/events`, `GET /api/v1/events/{id}` | Organizer/admin create, update, transition, archive |
| Ticket products | Included in public event detail | Organizer/admin create, update, archive |
| Inventory holds | None | Check, idempotent reserve, and idempotent release |
| Venues | None | Organizer/admin venue, room, block, and reservation APIs |

Publication fails unless schedule, category, venue/room capacity and availability, event capacity, and active ticket definitions are valid. Event-service never reads venue tables; it reserves availability through venue-service using the original organizer bearer token. Ticket inventory operations lock ticket rows and require `Idempotency-Key`, but they do not create bookings, payments, or issued tickets.

See the [Phase 3 architecture, state diagram, contracts, and complete example requests](docs/architecture/phase-3-event-venue-inventory.md).

## Phase 4 API surface

| Boundary | Paths | Behavior |
| --- | --- | --- |
| Attendee profile | `GET`, `PUT /api/v1/attendees/me` | JWT-subject-owned profile |
| Bookings | `POST`, `GET /api/v1/bookings`; `GET /api/v1/bookings/{id}` | Durable idempotency, owned history, event-backed hold |
| Tickets | `GET /api/v1/attendees/me/tickets` | Owned upcoming or complete ticket history |
| Scanning | `POST /api/v1/tickets/validate`; `POST /api/v1/check-ins` | Staff/organizer/admin validation and exactly-once check-in |
| Event inventory | Existing reserve/release plus `POST .../reservations/{id}/confirm` | Database-authoritative hold lifecycle |

Priced bookings stop at `PAYMENT_PENDING` and issue no ticket. Zero-price bookings confirm their event-service reservation and issue signed, PII-free QR tickets. Every POST requires `Idempotency-Key`. See the [Phase 4 architecture, concurrency rules, QR contract, and example](docs/architecture/phase-4-attendee-booking-ticketing.md).

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
- Event-service, venue-service, and attendee-service independently validate the JWT and enforce role plus ownership rules; the gateway is not their sole authorization boundary. Other domain services continue to deny unimplemented application routes.
- Kafka remains the only message broker. Event and attendee lifecycle changes publish through service-owned transactional outboxes; no code performs an ad-hoc database/Kafka dual write.

Completed through Phase 4: the Phase 1 platform foundation; Phase 2 authentication and gateway security; Phase 3 venues, events, ticket products, and inventory; and Phase 4 attendee profiles, registrations, booking/hold process state, zero-price ticket issuance, QR validation, check-in, and attendee lifecycle outbox. Payment providers, paid-booking completion/refunds, notification delivery, waitlisting, object storage, and deployment remain deferred.

See [system context](docs/architecture/system-context.md), [API conventions](docs/architecture/api-conventions.md), [security architecture](docs/architecture/security.md), and the [ADR index](docs/adr/README.md).
