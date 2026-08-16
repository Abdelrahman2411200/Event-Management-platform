# Architecture decision records

ADRs record decisions that constrain later implementation phases.

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](0001-kafka-only-messaging.md) | Kafka-only asynchronous messaging | Accepted |
| [0002](0002-database-per-service.md) | Database ownership by service | Accepted |
| [0003](0003-saga-and-transactional-outbox.md) | Orchestrated Saga and transactional outbox | Accepted |
| [0004](0004-api-versioning.md) | URI major-versioned public APIs | Accepted |
| [0005](0005-token-and-session-security.md) | Signed access tokens and rotated opaque refresh sessions | Accepted |
| [0006](0006-event-inventory-and-venue-assignment.md) | Locked inventory holds, venue API reservations, and event outbox | Accepted |
| [0007](0007-attendee-booking-ticket-and-check-in.md) | Durable booking commands, authoritative holds, signed tickets, and locked check-in | Accepted |
| [0008](0008-payment-provider-refunds-and-saga-recovery.md) | Provider-neutral payments, refund policy, and recoverable booking/payment Saga | Accepted |
| [0009](0009-kafka-failure-and-notification-delivery.md) | Bounded Kafka failure handling and durable notification delivery | Accepted |
