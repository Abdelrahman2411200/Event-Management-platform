CREATE TABLE booking_sagas (
    booking_id UUID PRIMARY KEY, payment_id UUID, state VARCHAR(48) NOT NULL,
    failure_code VARCHAR(80), failure_reason VARCHAR(500), recovery_attempts INTEGER NOT NULL DEFAULT 0,
    next_action_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_saga_booking FOREIGN KEY(booking_id) REFERENCES bookings(id) ON DELETE CASCADE
);
CREATE INDEX idx_booking_saga_recovery ON booking_sagas(state,next_action_at);
