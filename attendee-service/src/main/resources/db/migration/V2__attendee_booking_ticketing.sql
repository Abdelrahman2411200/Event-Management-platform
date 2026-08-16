CREATE TABLE attendee_profiles (
    id UUID PRIMARY KEY,
    display_name VARCHAR(160),
    phone_number VARCHAR(32),
    locale VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE booking_commands (
    id UUID PRIMARY KEY,
    attendee_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    event_id UUID NOT NULL,
    ticket_type_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    booking_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_booking_command_attendee_key UNIQUE (attendee_id, idempotency_key)
);

CREATE TABLE registrations (
    id UUID PRIMARY KEY,
    attendee_id UUID NOT NULL,
    event_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_registration_attendee FOREIGN KEY (attendee_id) REFERENCES attendee_profiles (id)
);

CREATE INDEX idx_registrations_attendee_event ON registrations (attendee_id, event_id);

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    attendee_id UUID NOT NULL,
    registration_id UUID NOT NULL UNIQUE,
    event_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL CHECK (total_amount >= 0),
    currency VARCHAR(3) NOT NULL,
    hold_expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_booking_attendee FOREIGN KEY (attendee_id) REFERENCES attendee_profiles (id),
    CONSTRAINT fk_booking_registration FOREIGN KEY (registration_id) REFERENCES registrations (id)
);

CREATE INDEX idx_bookings_attendee_created ON bookings (attendee_id, created_at DESC);
CREATE INDEX idx_bookings_event_status ON bookings (event_id, status);

CREATE TABLE booking_line_items (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    event_id UUID NOT NULL,
    event_organizer_id UUID NOT NULL,
    ticket_type_id UUID NOT NULL,
    event_title VARCHAR(240) NOT NULL,
    ticket_type_name VARCHAR(160) NOT NULL,
    event_starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    venue_id UUID NOT NULL,
    venue_space_id UUID,
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0),
    currency VARCHAR(3) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    line_total NUMERIC(19, 2) NOT NULL CHECK (line_total >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_line_item_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE
);

CREATE INDEX idx_line_items_booking ON booking_line_items (booking_id);

CREATE TABLE ticket_holds (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE,
    inventory_reservation_id UUID NOT NULL UNIQUE,
    event_id UUID NOT NULL,
    ticket_type_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    released_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ticket_hold_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE
);

CREATE INDEX idx_ticket_holds_status_expiry ON ticket_holds (status, expires_at);

CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    registration_id UUID NOT NULL,
    line_item_id UUID NOT NULL,
    attendee_id UUID NOT NULL,
    event_id UUID NOT NULL,
    event_organizer_id UUID NOT NULL,
    ticket_type_id UUID NOT NULL,
    event_title VARCHAR(240) NOT NULL,
    ticket_type_name VARCHAR(160) NOT NULL,
    event_starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    venue_id UUID NOT NULL,
    venue_space_id UUID,
    status VARCHAR(32) NOT NULL,
    token_version INTEGER NOT NULL DEFAULT 1,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    checked_in_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    refunded_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ticket_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_registration FOREIGN KEY (registration_id) REFERENCES registrations (id),
    CONSTRAINT fk_ticket_line_item FOREIGN KEY (line_item_id) REFERENCES booking_line_items (id),
    CONSTRAINT fk_ticket_attendee FOREIGN KEY (attendee_id) REFERENCES attendee_profiles (id)
);

CREATE INDEX idx_tickets_attendee_upcoming ON tickets (attendee_id, event_starts_at, status);
CREATE INDEX idx_tickets_event_status ON tickets (event_id, status);

CREATE TABLE check_ins (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL UNIQUE,
    event_id UUID NOT NULL,
    scanner_id UUID NOT NULL,
    checked_in_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_check_in_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id)
);

CREATE TABLE check_in_attempts (
    id UUID PRIMARY KEY,
    operation VARCHAR(32) NOT NULL,
    outcome VARCHAR(64) NOT NULL,
    scanner_id UUID NOT NULL,
    event_id UUID NOT NULL,
    ticket_id UUID,
    token_fingerprint VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    ticket_checked_in_at TIMESTAMP WITH TIME ZONE,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_scan_attempt_key UNIQUE (scanner_id, operation, idempotency_key)
);

CREATE INDEX idx_check_in_attempts_event_time ON check_in_attempts (event_id, attempted_at DESC);

CREATE TABLE attendee_outbox_messages (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL,
    payload TEXT NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    traceparent VARCHAR(256),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_attendee_outbox_unpublished
    ON attendee_outbox_messages (published_at, next_attempt_at, occurred_at);

CREATE TABLE processed_integration_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
