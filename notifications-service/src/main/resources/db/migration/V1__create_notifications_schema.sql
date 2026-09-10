CREATE TABLE notifications (
                               id UUID PRIMARY KEY,

                               event_id UUID NOT NULL,
                               order_id UUID NOT NULL,

                               type VARCHAR(50) NOT NULL,
                               recipient VARCHAR(255) NOT NULL,
                               message VARCHAR(1000) NOT NULL,

                               status VARCHAR(50) NOT NULL,

                               created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               sent_at TIMESTAMP WITH TIME ZONE,

                               CONSTRAINT uq_notification_event
                                   UNIQUE (event_id)
);

CREATE INDEX idx_notifications_order_id
    ON notifications (order_id);

CREATE INDEX idx_notifications_status
    ON notifications (status);