ALTER TABLE attendee_outbox_messages ADD COLUMN dead_lettered_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_attendee_outbox_dead_lettered
    ON attendee_outbox_messages (dead_lettered_at, occurred_at);
