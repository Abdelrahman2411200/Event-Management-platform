ALTER TABLE inventory_reservations
    ADD COLUMN confirmation_idempotency_key VARCHAR(128);

ALTER TABLE inventory_reservations
    ADD COLUMN confirmed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE inventory_reservations
    ADD CONSTRAINT uq_inventory_confirmation_idempotency UNIQUE (confirmation_idempotency_key);

CREATE INDEX idx_inventory_requester_event_status
    ON inventory_reservations (requester_id, event_id, status);
