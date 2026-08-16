CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY,
    reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE notification_recipients (
    user_id UUID PRIMARY KEY,
    email VARCHAR(320),
    phone_number VARCHAR(32),
    locale VARCHAR(16) NOT NULL,
    display_name VARCHAR(160),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE booking_notification_projections (
    booking_id UUID PRIMARY KEY,
    attendee_id UUID NOT NULL,
    event_id UUID NOT NULL,
    event_title VARCHAR(240) NOT NULL,
    event_starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_notification_bookings_event ON booking_notification_projections(event_id);
CREATE INDEX idx_notification_bookings_attendee ON booking_notification_projections(attendee_id);

CREATE TABLE notification_intents (
    id UUID PRIMARY KEY,
    notification_key VARCHAR(400) NOT NULL UNIQUE,
    source_message_id UUID NOT NULL,
    user_id UUID NOT NULL,
    business_id UUID NOT NULL,
    type VARCHAR(48) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    destination VARCHAR(320) NOT NULL,
    template_code VARCHAR(80) NOT NULL,
    locale VARCHAR(16) NOT NULL,
    variables_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    last_error VARCHAR(500),
    sent_at TIMESTAMP WITH TIME ZONE,
    dead_lettered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_notification_intents_due ON notification_intents(status,next_attempt_at);
CREATE INDEX idx_notification_intents_user ON notification_intents(user_id,created_at DESC);
CREATE INDEX idx_notification_intents_business ON notification_intents(business_id,type);

CREATE TABLE notification_delivery_attempts (
    id UUID PRIMARY KEY,
    intent_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    provider VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    provider_message_id VARCHAR(160),
    failure_reason VARCHAR(500),
    CONSTRAINT fk_notification_attempt_intent FOREIGN KEY(intent_id) REFERENCES notification_intents(id),
    CONSTRAINT uq_notification_attempt UNIQUE(intent_id,attempt_number)
);

CREATE TABLE local_deliveries (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(400) NOT NULL UNIQUE,
    channel VARCHAR(16) NOT NULL,
    destination VARCHAR(320) NOT NULL,
    subject VARCHAR(500),
    body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE processed_integration_events (
    message_id UUID PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
