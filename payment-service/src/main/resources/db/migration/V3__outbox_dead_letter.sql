ALTER TABLE payment_outbox_messages ADD COLUMN dead_lettered_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_payment_outbox_dead_lettered
    ON payment_outbox_messages (dead_lettered_at, occurred_at);
