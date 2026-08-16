# Kafka event catalog

Phase 6 keeps Apache Kafka as the platform's only broker. Topic names follow
`event-platform.<stream>.v<major>` and event types follow
`event-platform.<aggregate>.<past-tense-or-command>.v<schema>`.
Changing a payload incompatibly requires a new event-type schema suffix. Changing the
stream's compatibility boundary requires a new topic major version.

## Required metadata

Every produced record carries UTF-8 Kafka headers:

| Header | Rule |
| --- | --- |
| `messageId` | Globally unique UUID and canonical consumer deduplication key |
| `eventId` | Rolling-upgrade alias of `messageId` |
| `eventType` | Versioned, namespaced event or command name |
| `schemaVersion` | Positive payload schema version |
| `eventVersion` | Rolling-upgrade alias of `schemaVersion` |
| `occurredAt` | UTC ISO-8601 business occurrence time |
| `correlationId` | Original HTTP request or workflow correlation identifier |
| `traceparent` | Optional W3C trace continuation |
| `producer` | Owning publishing service |
| `aggregateType` / `aggregateId` | Service-owned aggregate and business partition key |

The Kafka record key is the aggregate identifier. Consumers accept the Phase 5 aliases
during rolling upgrades but all new producers emit both canonical and alias headers.

## Catalog

| Producer | Topic | Event type | Schema | Consumers | Business purpose |
| --- | --- | --- | ---: | --- | --- |
| event-service | `event-platform.event-lifecycle.v1` | `event-platform.event.published.v1` | 1 | Catalogued for projections | Event became publicly available |
| event-service | same | `event-platform.event.updated.v1` | 1 | notification-service | Lifecycle/schedule projection; reschedule alert only when a stored attendee schedule changes |
| event-service | same | `event-platform.event.cancelled.v1` | 1 | attendee-service, notification-service | Cancel owned booking/ticket projections and alert attendees |
| event-service | same | `event-platform.ticket-type.changed.v1` | 1 | Catalogued for projections | Ticket product snapshot changed |
| event-service | same | `event-platform.inventory.held.v1` | 1 | Catalogued for audit | Inventory hold created |
| event-service | same | `event-platform.inventory.confirmed.v1` | 1 | attendee-service | Advance booking Saga after authoritative inventory commit |
| event-service | same | `event-platform.inventory.released.v1` | 1 | Catalogued for audit | Inventory returned |
| event-service | same | `event-platform.inventory.expired.v1` | 1 | attendee-service | Expire a booking whose hold elapsed |
| event-service | same | `event-platform.inventory.confirmation-rejected.v1` | 1 | attendee-service | Trigger paid-booking compensation |
| event-service | same | `event-platform.inventory.release-rejected.v1` | 1 | Catalogued for operations | Diagnose a rejected release command |
| attendee-service | `event-platform.attendee-lifecycle.v1` | `event-platform.booking.created.v1` | 1 | notification-service | Durable booking receipt, recipient projection, and reminder scheduling |
| attendee-service | same | `event-platform.booking.payment-requested.v1` | 1 | payment-service | Create the payment-owned booking order projection |
| attendee-service | same | `event-platform.booking.confirmed.v1` | 1 | notification-service | Booking confirmation delivery |
| attendee-service | same | `event-platform.booking.payment-failed.v1` | 1 | Catalogued for audit | Booking-side failed-payment state |
| attendee-service | same | `event-platform.booking.refunded.v1` | 1 | Catalogued for audit | Booking/ticket refund state |
| attendee-service | same | `event-platform.ticket-hold.expired.v1` | 1 | Catalogued for audit | Local hold expiry |
| attendee-service | same | `event-platform.ticket.issued.v1` | 1 | payment-service, notification-service | Ticket projection plus signed QR delivery |
| attendee-service | same | `event-platform.ticket.checked-in.v1` | 1 | payment-service | Prevent refund of checked-in tickets |
| attendee-service | same | `event-platform.inventory.confirmation-requested.v1` | 1 | event-service | Idempotent booking Saga command |
| attendee-service | same | `event-platform.inventory.release-requested.v1` | 1 | event-service | Idempotent inventory compensation command |
| attendee-service | same | `event-platform.payment.compensation-requested.v1` | 1 | payment-service | Idempotent refund compensation command |
| payment-service | `event-platform.payment-lifecycle.v1` | `event-platform.payment.processing.v1` | 1 | attendee-service | Record non-terminal payment processing |
| payment-service | same | `event-platform.payment.succeeded.v1` | 1 | attendee-service, notification-service | Advance booking Saga and send payment confirmation |
| payment-service | same | `event-platform.payment.failed.v1` | 1 | attendee-service, notification-service | Fail booking payment flow and notify attendee |
| payment-service | same | `event-platform.refund.succeeded.v1` | 1 | attendee-service, notification-service | Invalidate refunded tickets and send confirmation |
| payment-service | same | `event-platform.refund.failed.v1` | 1 | Catalogued for operations | Diagnose provider refund failure |

## Retry and dead-letter convention

Consumer processing is at least once. Database effects and the processed `messageId` are
committed in one local transaction before the listener returns and its record offset is
acknowledged. Failures use bounded exponential backoff; an exhausted record is published
to `<source-topic>.dlt` at the original partition with Spring Kafka's original-record and
exception headers. There is no infinite retry loop.

Transactional outbox publication also uses bounded exponential backoff. After the configured
maximum, the owning outbox row receives `dead_lettered_at` and remains available for operator
inspection; it is never silently deleted.
