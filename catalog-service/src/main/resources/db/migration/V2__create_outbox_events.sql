CREATE TABLE outbox_events (
                               id UUID PRIMARY KEY,
                               aggregate_type VARCHAR(100) NOT NULL,
                               aggregate_id UUID NOT NULL,
                               type VARCHAR(100) NOT NULL,
                               payload TEXT NOT NULL,
                               occurred_at TIMESTAMPTZ NOT NULL,
                               published_at TIMESTAMPTZ,
                               claimed_until TIMESTAMPTZ,
                               retry_count INTEGER NOT NULL DEFAULT 0,
                               last_error TEXT,
                               correlation_id VARCHAR(255)
);

CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (published_at, occurred_at);