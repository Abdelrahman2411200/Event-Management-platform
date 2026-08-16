# Payment and booking Saga recovery

## Normal automatic recovery

Payment-service scans `PROCESSING` payments older than `PAYMENT_PROCESSING_TIMEOUT` and asks the configured provider adapter for authoritative status. Applying that result is idempotent and monotonic. Attendee-service scans due `INVENTORY_CONFIRMATION_PENDING` and `COMPENSATION_PENDING` Sagas, republishes the stable command, and applies bounded exponential backoff.

Transactional outbox relays retry Kafka publication. Never repair a stuck Saga by editing another service's database or by marking a browser callback successful.

## Triage order

1. Check `/actuator/health/readiness` for attendee, event, payment, PostgreSQL, and Kafka.
2. Inspect outbox unpublished age and `last_error` in the owning database. Restore Kafka connectivity before changing domain state.
3. Query the payment API and transaction history. `POST /api/v1/payments/{id}/verify` performs an authorized provider check.
4. Inspect the booking response's `saga.state`, `failureCode`, `recoveryAttempts`, and `nextActionAt`.
5. Verify event-service reservation state by reservation ID. A confirmed reservation is replay-safe; an expired/released reservation requires payment compensation.
6. For a failed compensation, restore provider access and let the same deterministic compensation key retry. Escalate only after comparing provider transaction history with payment-service transactions.

## Invariants

- `CONFIRMED` booking requires event-service `CONFIRMED` inventory and issued tickets.
- A settled payment with unavailable inventory must be `COMPENSATION_PENDING` or refunded; it must not remain silently paid.
- A payment/refund provider object is applied once even when the API or webhook is duplicated.
- A refunded ticket is never check-in eligible and confirmed refunded inventory is not returned to sale in Phase 5.
- Out-of-order failure cannot regress `SUCCEEDED`, `PARTIALLY_REFUNDED`, or `REFUNDED`.

For local testing, set `PAYMENT_WEBHOOK_SECRET`, use the fake adapter tokens documented in the Phase 5 architecture, and send the lowercase hexadecimal HMAC-SHA256 body signature as `X-Payment-Signature`.
