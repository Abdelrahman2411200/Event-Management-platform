# ADR 0008: Provider-neutral payments, refund policy, and recoverable Saga

- Status: Accepted
- Date: 2026-08-16

## Context

Paid booking completion spans attendee-owned booking/ticket state, event-owned inventory, and payment-owned provider transactions. Provider calls and Kafka delivery can be duplicated, delayed, or arrive out of order. A browser redirect is not proof that money settled, and raw card data must not enter this platform.

## Decision

Payment providers implement one payment-service port for create, verify, signed webhook, and full/partial refund operations. Phase 5 ships a deterministic local fake adapter; provider secrets remain environment configuration. Payment method tokens are fingerprinted and discarded, raw card/CVV data is never accepted or stored, and webhook bodies are retained only as SHA-256 fingerprints.

Attendee-service is the Saga orchestrator. It consumes verified payment events, sends idempotent Kafka inventory commands, and confirms a booking only after event-service confirms the held inventory. Every participant commits only its database plus its transactional outbox. Consumers deduplicate message IDs and business commands carry stable keys. No XA transaction or cross-service database access is permitted.

Attendees may refund their entire remaining booking before the event starts. The owning organizer and administrators may refund selected, non-checked-in tickets or the entire remaining booking. Provider capabilities still apply. A successful refund invalidates the selected ticket tokens. Confirmed refunded capacity is not returned for resale in Phase 5; only failed or expired unconfirmed holds are released.

Stuck payment processing and Saga confirmation/compensation states are recoverable. Payment-service verifies old `PROCESSING` records through the provider port. Attendee-service republishes stable confirmation or compensation commands with bounded backoff. Monotonic state transitions ignore late failures after settlement.

## Consequences

The workflow is eventually consistent and operators must monitor outbox age and non-terminal states. Provider adapters are replaceable without changing domain state. Duplicate requests and transport delivery are safe, payment proof is server-to-server, and a permanently unavailable inventory hold converges to a compensating refund rather than a paid, ticketless booking.
