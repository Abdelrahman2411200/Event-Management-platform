ALTER TABLE outbox_messages ADD COLUMN dead_lettered_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_outbox_dead_lettered ON outbox_messages (dead_lettered_at, occurred_at);
