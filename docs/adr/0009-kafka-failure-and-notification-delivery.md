# ADR 0009: Bound Kafka failures and persist notification delivery

- Status: Accepted
- Date: 2026-08-16

## Context

At-least-once Kafka delivery can duplicate messages, while poison records and provider outages
can otherwise create infinite retry loops. Reminder timers and notification attempts must survive
process restarts and horizontal scaling.

## Decision

Kafka consumers use record acknowledgement, local database transactions, processed message IDs,
bounded exponential retry, and a source-topic `.dlt`. Outbox relays also have a bounded attempt
count and retain exhausted rows as dead-lettered operational state.

Notification-service stores deterministic intent keys, delivery attempts, recipient/preferences
projections, and reminder schedules in its own PostgreSQL database. Provider ports receive the
same deterministic idempotency key. Email/SMS local adapters persist inspectable output and real
provider credentials remain optional and external.

## Consequences

Failures stop consuming resources indefinitely and remain diagnosable and replayable. Delivery is
eventually consistent and requires DLT/outbox/intent operations. Database locking provides a safe
horizontal-scaling baseline, while high-throughput deployments may later introduce skip-locked
claim leasing without changing intent semantics.
