# ADR 0007: Persist booking commands, use database-authoritative holds, and sign minimal QR tickets

- Status: Accepted
- Date: 2026-08-16

## Context

Phase 4 must coordinate an attendee-owned booking with event-owned inventory without a distributed transaction. Client and network retries must not duplicate either resource. Inventory cannot oversell, even without Redis. Issued tickets need tamper detection and check-in must be exactly-once at the business-state level.

## Decision

Attendee-service persists a unique booking-command claim before calling event-service and derives stable downstream reservation and confirmation keys from it. Event-service remains authoritative and pessimistically locks the ticket row for hold, confirm, release, and expiry. Each service commits only its own database.

Paid bookings remain `PAYMENT_PENDING`; Phase 4 confirms and issues only zero-price bookings. Event inventory and attendee lifecycle changes publish through their respective transactional outboxes. Event expiry and cancellation consumers deduplicate by message ID.

Ticket QR tokens are HMAC-SHA256 signed, contain only issuer, opaque ticket/event IDs, token version, and issue time, and use externally supplied key material. Check-in locks the attendee-owned ticket row, stores one unique check-in, and audits every authorized scan using only a token fingerprint.

## Consequences

Retries can resume after either side commits without creating another booking or hold. PostgreSQL remains correct when Redis is unavailable. HTTP failure can leave a recoverable pending command rather than hiding uncertainty. HMAC validation requires attendee-service validators to share protected key material; asymmetric signing may replace it when independently deployed scanners need offline validation. Payment completion, refund orchestration, and per-event staff assignments remain explicit later-phase work.
