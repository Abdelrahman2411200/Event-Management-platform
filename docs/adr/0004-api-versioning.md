# ADR 0004: Version public APIs in the URI

- Status: Accepted
- Date: 2026-08-15

## Context

Clients need an obvious, routable compatibility boundary. Header negotiation is less visible in browser tooling, gateway rules, logs, and support diagnostics.

## Decision

Public application endpoints start with `/api/v1`. Compatible additive changes remain in the current major version. A breaking request or response change introduces a new major path such as `/api/v2` and a documented migration window. Actuator and OpenAPI infrastructure endpoints are not placed under `/api/v1`.

## Consequences

Gateway routes and OpenAPI documents make the supported major version explicit. Multiple major versions may coexist temporarily and require separate tests. Teams must avoid creating a new version for ordinary additive evolution.
