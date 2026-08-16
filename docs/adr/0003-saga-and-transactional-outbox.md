# ADR 0003: Use an orchestrated Saga and transactional outbox

- Status: Accepted
- Date: 2026-08-15

## Context

The future booking/payment workflow spans attendee and payment ownership. Database commits and Kafka publication cannot participate safely in one distributed transaction, and transport delivery is at least once.

## Decision

The attendee service will own booking process state and orchestrate the booking/payment Saga through Kafka. Each participating service commits only local state. When a local state change must publish an event, the service writes a service-owned outbox record in the same transaction and publishes it through a relay. Consumers deduplicate by event ID or make the state transition atomically idempotent.

Compensating commands and terminal failure states are part of the workflow contract. No XA/two-phase commit or cross-database transaction is used.

## Consequences

Workflows become eventually consistent and require observable process state, timeouts, retry limits, and operator recovery paths. The system gains reliable publication and clear ownership at the cost of additional workflow and outbox storage. Phase 3 added the first concrete outbox for event lifecycle contracts. Phase 4 added attendee-owned booking state and its outbox. Phase 5 completed paid booking confirmation, payment/refund outbox publication, idempotent inventory commands, compensation, and scheduled recovery without introducing a distributed transaction. Phase 6 bounds relay attempts and retains exhausted rows with explicit dead-letter state.
