# Kafka and notification recovery

## Consumer dead-letter topics

1. Check `platform.kafka.consumer.retries` and `platform.kafka.consumer.dead.lettered` in Prometheus.
2. Locate the log entry containing topic, partition, offset, record key, and exception.
3. Inspect `<source-topic>.dlt`; retain its original headers when exporting a record.
4. Correct schema/configuration or the downstream outage before replay.
5. Replay with the original `messageId`. Already committed consumers will ignore it; an uncommitted
   poison record will apply once after the cause is fixed.

Never edit another service's processed-message table or publish a replacement with a random ID
merely to bypass deduplication.

## Outbox dead letters

Query the owning service's outbox for `dead_lettered_at IS NOT NULL`, then inspect `event_type`,
`aggregate_id`, `publish_attempts`, and `last_error`. Restore Kafka connectivity or correct the
record/configuration. Requeue only through an audited owner-service operation that clears the
dead-letter timestamp and sets a new `next_attempt_at`; do not copy the payload into another
service's database.

## Notification delivery

Notification intent status and every provider attempt are durable. For `RETRY_SCHEDULED`, restore
the configured provider and allow the worker to retry. For `DEAD_LETTERED`, compare the provider's
delivery history using the intent's deterministic `notification_key` before requeueing, because a
real provider may have accepted a request whose response was lost.

Local development output is visible in `local_deliveries` or the admin-protected
`/api/v1/notifications/local-deliveries` endpoint. No external email/SMS credentials are needed.
