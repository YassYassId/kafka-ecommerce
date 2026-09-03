ALTER TABLE outbox_events
    ADD COLUMN claimed_until TIMESTAMP WITH TIME ZONE;