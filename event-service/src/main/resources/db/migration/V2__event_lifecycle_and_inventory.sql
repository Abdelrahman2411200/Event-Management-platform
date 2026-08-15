CREATE TABLE event_categories (
    id UUID PRIMARY KEY,
    slug VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_event_categories_status_name ON event_categories (status, name);

CREATE TABLE managed_events (
    id UUID PRIMARY KEY,
    organizer_id UUID NOT NULL,
    title VARCHAR(240) NOT NULL,
    description VARCHAR(10000) NOT NULL,
    category_id UUID NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    venue_id UUID NOT NULL,
    venue_space_id UUID,
    venue_reservation_id UUID,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    status VARCHAR(32) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_events_category FOREIGN KEY (category_id) REFERENCES event_categories (id),
    CONSTRAINT chk_event_schedule CHECK (ends_at > starts_at)
);

CREATE INDEX idx_events_organizer_status ON managed_events (organizer_id, status);
CREATE INDEX idx_events_public_discovery ON managed_events (status, starts_at, category_id);
CREATE INDEX idx_events_venue_schedule ON managed_events (venue_id, starts_at, ends_at);

CREATE TABLE ticket_types (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    price NUMERIC(19, 2) NOT NULL CHECK (price >= 0),
    currency VARCHAR(3) NOT NULL,
    allocation INTEGER NOT NULL CHECK (allocation > 0),
    reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    sales_start TIMESTAMP WITH TIME ZONE NOT NULL,
    sales_end TIMESTAMP WITH TIME ZONE NOT NULL,
    min_quantity INTEGER NOT NULL CHECK (min_quantity > 0),
    max_quantity INTEGER NOT NULL CHECK (max_quantity >= min_quantity),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ticket_types_event FOREIGN KEY (event_id) REFERENCES managed_events (id) ON DELETE CASCADE,
    CONSTRAINT uq_ticket_type_name UNIQUE (event_id, name),
    CONSTRAINT chk_ticket_sales_window CHECK (sales_end > sales_start),
    CONSTRAINT chk_ticket_reserved_allocation CHECK (reserved_quantity <= allocation)
);

CREATE INDEX idx_ticket_types_event_status ON ticket_types (event_id, status);
CREATE INDEX idx_ticket_types_sales_window ON ticket_types (sales_start, sales_end, status);

CREATE TABLE inventory_reservations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    ticket_type_id UUID NOT NULL,
    requester_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(32) NOT NULL,
    reserve_idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    release_idempotency_key VARCHAR(128) UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inventory_event FOREIGN KEY (event_id) REFERENCES managed_events (id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES ticket_types (id) ON DELETE CASCADE
);

CREATE INDEX idx_inventory_ticket_status_expiry
    ON inventory_reservations (ticket_type_id, status, expires_at);
CREATE INDEX idx_inventory_requester_status
    ON inventory_reservations (requester_id, status);

CREATE TABLE outbox_messages (
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

CREATE INDEX idx_outbox_unpublished ON outbox_messages (published_at, next_attempt_at, occurred_at);
