# Phase 6 Kafka backbone and notification delivery

## Reliability model

Event-service, attendee-service, and payment-service continue to write business state and
their outgoing event to a service-owned transactional outbox in one database transaction.
Relays publish records with the canonical metadata in the [Kafka event catalog](kafka-event-catalog.md),
wait for broker acknowledgement, and then mark the outbox row published. Publication attempts
are bounded; exhausted rows are retained with `dead_lettered_at`, attempt count, and last error.

Consumers use record acknowledgement with database transactions. A listener returns only after
its business state, deterministic effects, and processed-message ID are durable. Failed records
receive four configurable exponential-backoff retries by default and are then written to the
source topic's `.dlt` topic. Retry and dead-letter counters plus topic/partition/offset logs make
poison messages diagnosable. Spring Kafka client metrics expose consumer lag through Prometheus.

## Notification ownership

Notification-service owns recipient projections, preferences, booking reminder projections,
notification intents, delivery attempts, inspectable local delivery output, and processed Kafka
IDs. It never queries attendee, payment, or event tables. Contact and immutable display snapshots
arrive in versioned lifecycle events.

Each intent has a unique deterministic key:

```text
<notification-type>:<business-id>:<channel>
```

Repeated delivery of the same Kafka record is rejected by `messageId`; a logically duplicated
event with a new message ID converges on the deterministic notification key. Provider ports also
receive that key and must use it as their provider idempotency key. The local email/SMS adapters
persist one `local_deliveries` row per key, so a retry cannot create a second local delivery.

## Delivery states and attempts

Intents move through `PENDING`, `RETRY_SCHEDULED`, `SENT`, `DEAD_LETTERED`, or `CANCELLED`.
Every provider call creates a numbered delivery-attempt row. Provider failures use bounded
exponential backoff; the fifth failed attempt dead-letters the intent by default. Restoring a
provider allows the same intent and idempotency key to resume without creating another intent.

Email and SMS are separate ports. `local-email` and `local-sms` require no credentials and store
rendered messages in PostgreSQL. Future real adapters must remain optional, externalize every
credential, and preserve the deterministic provider idempotency key.

## Preferences and mandatory messages

Users can enable or disable reminders and opt in to SMS at
`GET/PUT /api/v1/notifications/preferences`. Booking receipts/confirmations, payment results,
ticket/QR delivery, event cancellation/reschedule, and refund confirmations are mandatory
transactional email and cannot be silently disabled. Disabling reminders cancels only pending
optional reminder intents.

## Restart-safe reminders and scaling

Booking events persist the event start snapshot and create a reminder intent scheduled at
`eventStartsAt - NOTIFICATION_REMINDER_LEAD` (24 hours by default). There are no in-memory timers.
Scheduled workers query due rows under pessimistic database locks, so process restarts retain
the schedule and multiple Kubernetes replicas cannot intentionally claim the same intent at once.
Event schedule changes update pending reminder rows; cancellation cancels them.

## Templates and local inspection

Templates are selected by notification type, channel, and locale with an English fallback.
Rendering is strict: an unresolved `{{variable}}` fails the attempt instead of sending malformed
content. Authenticated users can inspect their intent status at `GET /api/v1/notifications`.
Administrators can inspect the most recent local adapter payloads at
`GET /api/v1/notifications/local-deliveries`.
