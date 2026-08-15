# Event Management Platform

A local-first, cloud-ready event management platform organized as independently buildable Spring Boot services and a React frontend.

Phase 1 is a foundation release. It provides service boundaries, build and test infrastructure, versioning and error conventions, local data and messaging infrastructure, observability, Docker execution, CI, and architecture decisions. It intentionally does **not** implement authentication, event, venue, attendee, booking, payment, or notification business features.

## Technology baseline

- Java 17, Spring Boot 3.5.14, Spring Security, Spring Cloud Gateway, Spring Data JPA
- React 19, TypeScript, Vite, Tailwind CSS
- PostgreSQL, Redis, Apache Kafka in KRaft mode
- Flyway for all service-owned database migrations
- Docker and Docker Compose
- Prometheus, Grafana, Loki, OpenTelemetry Collector, and Jaeger
- GitHub Actions for backend and frontend validation

Kubernetes, Helm, Terraform, AWS, and cloud-managed services are future deployment concerns. They are not needed to build, test, or run Phase 1 locally. MinIO is not included because this phase has no object-storage use case; a local S3-compatible adapter will be added when a real use case exists.

## Repository structure

```text
.
├── api-gateway/              # Reactive Spring Cloud Gateway
├── auth-service/             # Authentication boundary skeleton
├── event-service/            # Event boundary skeleton
├── venue-service/            # Venue boundary skeleton
├── attendee-service/         # Attendee and future booking boundary skeleton
├── payment-service/          # Payment boundary skeleton
├── notification-service/     # Notification boundary skeleton
├── frontend/                 # React + TypeScript + Tailwind shell
├── shared/
│   ├── platform-contracts/   # Domain-free error/correlation contracts
│   ├── platform-web/         # Servlet error, security, OpenAPI, metadata baseline
│   └── data-service-parent/  # Build-only Maven parent for data-owning services
├── docker/                   # Backend image and PostgreSQL bootstrap
├── observability/            # Local Prometheus, Loki, OTel, and Grafana config
├── docs/                     # Architecture, conventions, and ADRs
├── compose.yaml
├── Makefile
└── pom.xml
```

The shared modules contain only narrow technical code. They contain no business entities, repositories, services, or domain events.

## Prerequisites

- JDK 17
- Maven 3.9+
- Node.js 20.19+ (Node 24 is used in CI)
- npm 11+
- Docker with Docker Compose

No AWS account, credentials, Kubernetes cluster, or external provider account is required.

## Build and test

From the repository root:

```bash
mvn clean verify
```

Useful backend commands:

```bash
# Compile every Maven module without tests
mvn clean compile

# Run every backend test
mvn test

# Package one service and everything it depends on
mvn -pl event-service -am package -DskipTests
```

Frontend commands:

```bash
npm --prefix frontend ci
npm --prefix frontend audit --audit-level=high
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run build
npm --prefix frontend run dev
```

The repository also provides equivalent Make targets:

```bash
make build
make test
make frontend-check
```

## Run locally

### Core infrastructure only

Starts PostgreSQL, Redis, and Kafka and waits for their health checks:

```bash
docker compose up -d --wait
```

Stop core infrastructure without deleting data:

```bash
docker compose down
```

### Core infrastructure plus observability

```bash
docker compose --profile observability up -d --wait
```

### Complete stack

Builds and starts all backend services, the frontend, core infrastructure, and observability:

```bash
docker compose --profile full-stack up -d --build --wait
```

Open the frontend at [http://localhost:3000](http://localhost:3000).

Stop every profile without deleting volumes:

```bash
docker compose --profile full-stack --profile observability down
```

To remove local development data as well, add `--volumes` to that final command. This permanently deletes the Compose-managed PostgreSQL, Redis, Kafka, Prometheus, Grafana, and Loki volumes.

Optional port and local-only credential overrides are documented in `.env.example`. Copy it to `.env` only when an override is needed. Never place real credentials in committed files or reuse the included development values outside a local machine.

## Local endpoints

| Component | Port | Health | OpenAPI / UI |
| --- | ---: | --- | --- |
| Frontend | 3000 | `/` | — |
| API gateway | 8080 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| Auth service | 8081 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| Event service | 8082 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| Venue service | 8083 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| Attendee service | 8084 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| Payment service | 8085 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| Notification service | 8086 | `/actuator/health/readiness` | `/v3/api-docs`, `/swagger-ui/index.html` |
| PostgreSQL | 5432 | Compose health check | — |
| Redis | 6379 | Compose health check | — |
| Kafka | 9092 | Compose health check | — |
| Prometheus | 9090 | `/-/ready` | [http://localhost:9090](http://localhost:9090) |
| Grafana | 3001 | `/api/health` | [http://localhost:3001](http://localhost:3001) |
| Loki | 3100 | `/ready` | — |
| Jaeger | 16686 | — | [http://localhost:16686](http://localhost:16686) |
| OTel Collector | 4317 / 4318 | — | OTLP gRPC / HTTP |

The PostgreSQL container creates six logical databases with distinct owners: `auth_service`, `event_service`, `venue_service`, `attendee_service`, `payment_service`, and `notification_service`. Only the owning service may access its database.

## API and operational conventions

- Public application endpoints begin with `/api/v1`.
- Every backend exposes liveness, readiness, service info, Prometheus metrics, OpenAPI JSON, and Swagger UI where appropriate. These operational endpoints are available without application authentication for local health checks and Prometheus scraping.
- Domain-service readiness includes the service-owned database. Kafka is present for future asynchronous contracts but is not used by a Phase 1 business flow.
- `X-Correlation-Id` is accepted or generated at the edge, forwarded downstream, returned to callers, and included in logging context.
- W3C `traceparent` propagation is handled independently through Micrometer and OpenTelemetry.
- API failures use the shared error shape with `timestamp`, `status`, `error`, `code`, `message`, `path`, `correlationId`, and `validationDetails`.
- Operational and documentation endpoints are public in Phase 1. All other direct domain-service requests are denied until authentication and explicit endpoint policies are implemented.

See [system context](docs/architecture/system-context.md), [API conventions](docs/architecture/api-conventions.md), and the [ADR index](docs/adr/README.md) for the binding architecture rules.

## Phase status

Completed in Phase 1:

- Buildable API gateway and six data-owning Spring Boot services
- React, TypeScript, and Tailwind frontend shell
- Flyway baseline per service with no premature business tables
- PostgreSQL connection privileges that restrict each service login to its owned database
- Kafka-only local messaging infrastructure
- Database-per-service ownership and local credentials
- Standard API errors, correlation IDs, OpenAPI, actuator probes, metadata, metrics, and tracing export
- Local observability profile and full-stack Compose profile
- CI and exact developer commands
- ADRs for Kafka, database ownership, Saga/outbox, and API versioning

Deferred to later phases:

- Authentication, JWT, refresh tokens, RBAC, and OAuth2
- Event, venue, attendee, registration, ticketing, check-in, and booking behavior
- Payment processing, verification, refunds, and provider adapters
- Email/SMS delivery and provider adapters
- Object storage, maps, and AI adapters
- Kafka event schemas, topics, consumers, outbox tables, and the booking/payment Saga implementation
- Production log shipping rules and alerting
- Kubernetes, Helm, Terraform, and AWS deployment

Phase 2 business features should start only after choosing a bounded capability and its contract. They must preserve the decisions recorded under `docs/adr/`.
