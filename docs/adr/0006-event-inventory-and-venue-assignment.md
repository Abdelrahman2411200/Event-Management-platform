# ADR 0006: Use locked inventory holds, venue API reservations, and the event outbox

- Status: Accepted
- Date: 2026-08-16

## Context

Phase 3 introduces event publication, ticket allocations, and venue scheduling across independently owned databases. Publication must reject capacity or schedule conflicts. Future booking requests must not oversell inventory, retries must be safe, and lifecycle events must not use an unreliable database/Kafka dual write.

## Decision

The event service stores only venue and room UUIDs. Publication synchronously calls the versioned venue API with the organizer bearer token and an idempotent `event:<eventId>` owner reference. The venue service serializes availability decisions by locking its venue row and stores the resulting reservation in its own database.

Ticket definitions and temporary inventory reservations belong to event-service. Reserve and release commands require idempotency keys. Ticket rows are pessimistically locked while expired holds are returned and reserved quantity is changed, preventing allocation oversell. Holds are not bookings, payments, or issued attendee tickets.

Event lifecycle and ticket-definition changes write a local outbox row in the same transaction. A relay publishes versioned contracts to Kafka with standard correlation headers. Direct post-commit publication is forbidden.

## Consequences

Venue validation is immediately consistent within venue-service, and inventory capacity is immediately consistent within event-service. Publication requires the venue service to be available and fails closed otherwise. Cancellation releases the venue reservation before changing local state; repeated release is convergent. Kafka delivery is at least once, so consumers must deduplicate by event ID. The later booking/payment Saga can reserve event inventory without owning or querying event tables.
