CREATE TABLE booking_payment_orders (
    booking_id UUID PRIMARY KEY, attendee_id UUID NOT NULL, event_id UUID NOT NULL,
    event_organizer_id UUID NOT NULL, inventory_reservation_id UUID NOT NULL,
    ticket_type_id UUID NOT NULL, quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,2) NOT NULL CHECK (unit_price >= 0),
    total_amount NUMERIC(19,2) NOT NULL CHECK (total_amount > 0), currency VARCHAR(3) NOT NULL,
    event_starts_at TIMESTAMP WITH TIME ZONE NOT NULL, hold_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE payments (
    id UUID PRIMARY KEY, booking_id UUID NOT NULL UNIQUE, attendee_id UUID NOT NULL,
    event_id UUID NOT NULL, amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    refunded_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0),
    currency VARCHAR(3) NOT NULL, provider VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(160), status VARCHAR(40) NOT NULL,
    failure_code VARCHAR(80), failure_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_payments_attendee_created ON payments(attendee_id, created_at DESC);
CREATE INDEX idx_payments_status_updated ON payments(status, updated_at);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY, payment_id UUID NOT NULL, attendee_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL, payment_method_fingerprint VARCHAR(64) NOT NULL,
    provider_attempt_id VARCHAR(160), status VARCHAR(40) NOT NULL,
    failure_code VARCHAR(80), failure_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_attempt_payment FOREIGN KEY(payment_id) REFERENCES payments(id),
    CONSTRAINT uq_payment_attempt_actor_key UNIQUE(attendee_id, idempotency_key)
);

CREATE TABLE refunds (
    id UUID PRIMARY KEY, payment_id UUID NOT NULL, booking_id UUID NOT NULL,
    requested_by UUID NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK(amount > 0), currency VARCHAR(3) NOT NULL,
    ticket_ids TEXT, reason VARCHAR(500), provider_refund_id VARCHAR(160),
    status VARCHAR(40) NOT NULL, failure_code VARCHAR(80), failure_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refund_payment FOREIGN KEY(payment_id) REFERENCES payments(id),
    CONSTRAINT uq_refund_actor_key UNIQUE(requested_by, idempotency_key)
);

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY, payment_id UUID NOT NULL, attempt_id UUID, refund_id UUID,
    type VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL,
    amount NUMERIC(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
    provider_transaction_id VARCHAR(160), provider_event_id VARCHAR(160),
    failure_code VARCHAR(80), occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_transaction_payment FOREIGN KEY(payment_id) REFERENCES payments(id)
);
CREATE INDEX idx_transactions_payment_time ON payment_transactions(payment_id, occurred_at);

CREATE TABLE provider_webhook_events (
    id UUID PRIMARY KEY, provider VARCHAR(40) NOT NULL, provider_event_id VARCHAR(160) NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL, event_type VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL, received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE, failure_reason VARCHAR(500),
    CONSTRAINT uq_provider_webhook_event UNIQUE(provider, provider_event_id)
);

CREATE TABLE payment_ticket_projections (
    ticket_id UUID PRIMARY KEY, booking_id UUID NOT NULL, attendee_id UUID NOT NULL,
    event_id UUID NOT NULL, status VARCHAR(32) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_payment_tickets_booking ON payment_ticket_projections(booking_id, status);

CREATE TABLE payment_outbox_messages (
    id UUID PRIMARY KEY, aggregate_type VARCHAR(80) NOT NULL, aggregate_id UUID NOT NULL,
    event_type VARCHAR(160) NOT NULL, event_version INTEGER NOT NULL, payload TEXT NOT NULL,
    correlation_id VARCHAR(128) NOT NULL, traceparent VARCHAR(256),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, published_at TIMESTAMP WITH TIME ZONE,
    publish_attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(500), version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_payment_outbox_unpublished ON payment_outbox_messages(published_at,next_attempt_at,occurred_at);

CREATE TABLE processed_integration_events (
    event_id UUID PRIMARY KEY, event_type VARCHAR(160) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
