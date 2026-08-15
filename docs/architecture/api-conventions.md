# API, error, and propagation conventions

## Versioning

All public application routes use a URI major version: `/api/v1/<resource>`. Backward-compatible fields and endpoints remain within `v1`; a breaking contract requires `/api/v2`. Actuator and OpenAPI infrastructure paths are not application APIs and remain `/actuator/**`, `/v3/api-docs`, and `/swagger-ui/**`.

OpenAPI JSON is exposed by every service at `/v3/api-docs`, and Swagger UI is exposed at `/swagger-ui/index.html`. The API gateway documents its edge contract; service documents remain available on their local development ports.

## Standard error body

Servlet services share only the domain-free error contract and handling utilities. The reactive gateway emits the same shape:

```json
{
  "timestamp": "2026-08-15T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/events",
  "correlationId": "8c5f2fd1-49fa-4e14-a986-9206da42fd93",
  "validationDetails": [
    {
      "field": "name",
      "message": "must not be blank"
    }
  ]
}
```

`validationDetails` is an empty array when not applicable. Error messages must be safe for callers and must not expose stack traces, SQL, credentials, provider payloads, or internal topology.

## Correlation and tracing

At the HTTP edge:

1. Accept `X-Correlation-Id` when it contains 1–128 safe ASCII identifier characters.
2. Generate a UUID when the header is absent or invalid.
3. Forward the value downstream, return it in the response, and place it in logging context as `correlationId`.
4. Propagate the W3C `traceparent` header independently through Micrometer/OpenTelemetry. A correlation ID groups a business request; trace and span IDs describe a particular distributed execution.

Future Kafka records use these headers:

| Header | Purpose |
| --- | --- |
| `eventId` | Globally unique event identity used for deduplication |
| `eventType` | Stable namespaced event name |
| `eventVersion` | Positive integer schema version |
| `occurredAt` | UTC event time in ISO-8601 form |
| `producer` | Publishing service name |
| `correlationId` | Original request/workflow correlation value |
| `traceparent` | W3C trace continuation when available |

Consumers start a new trace when no valid trace context exists, while preserving `correlationId` across the workflow.
