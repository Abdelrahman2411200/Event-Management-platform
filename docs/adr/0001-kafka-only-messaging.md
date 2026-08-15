# ADR 0001: Use Apache Kafka as the only message broker

- Status: Accepted
- Date: 2026-08-15

## Context

The platform needs asynchronous integration events, workflow messages, replayable history, and horizontal consumer scaling. Supporting multiple brokers in early phases would duplicate operational and application patterns without a demonstrated need.

## Decision

Apache Kafka is the only asynchronous messaging platform. RabbitMQ and other brokers are not added. Event schemas are versioned, consumers assume at-least-once delivery, and correlation plus W3C trace context travel in Kafka headers.

## Consequences

Local development runs one KRaft Kafka broker. Future production topology may add brokers, partitions, schema governance, access control, and dead-letter handling, while preserving application semantics. Teams learn and operate one messaging model, and consumers must implement idempotency.
