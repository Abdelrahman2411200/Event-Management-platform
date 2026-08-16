CREATE TABLE processed_integration_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
