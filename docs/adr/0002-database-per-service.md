# ADR 0002: Enforce database ownership by service

- Status: Accepted
- Date: 2026-08-15

## Context

Shared schemas make service boundaries porous, couple deployments, and allow one service to bypass another's invariants. Local development still needs to remain affordable and simple.

## Decision

Every data-owning service has its own PostgreSQL database, credentials, Flyway migrations, entities, and repositories. A service must not query or map another service's tables, and cross-service JPA relationships are forbidden. Local Compose hosts the logical databases in one PostgreSQL container; infrastructure placement does not change ownership.

## Consequences

Cross-service reads require an API or an event-fed projection. Multi-service changes cannot use a database transaction and instead use an explicit workflow. Migrations and restore procedures can evolve independently. Local operation remains lightweight while production can isolate databases further.
