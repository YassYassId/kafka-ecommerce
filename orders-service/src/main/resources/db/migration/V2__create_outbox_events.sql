CREATE TABLE outbox_events (
                               id UUID PRIMARY KEY,
                               aggregate_type VARCHAR(100) NOT NULL,
                               aggregate_id UUID NOT NULL,
                               event_type VARCHAR(100) NOT NULL,
                               event_version INTEGER NOT NULL,
                               payload JSONB NOT NULL,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               published_at TIMESTAMP WITH TIME ZONE,
                               retry_count INTEGER NOT NULL DEFAULT 0,
                               last_error TEXT,

                               CONSTRAINT chk_outbox_retry_count
                                   CHECK (retry_count >= 0)
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id);