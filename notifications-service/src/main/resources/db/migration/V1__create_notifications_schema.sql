-- =====================================================
-- Notifications table
-- =====================================================

CREATE TABLE notifications (
                               id UUID PRIMARY KEY,
                               order_id UUID NOT NULL,
                               type VARCHAR(30) NOT NULL,
                               channel VARCHAR(30) NOT NULL,
                               recipient VARCHAR(255) NOT NULL,
                               status VARCHAR(30) NOT NULL,
                               message TEXT NOT NULL,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               sent_at TIMESTAMP WITH TIME ZONE,

                               CONSTRAINT chk_notifications_type
                                   CHECK (type IN ('ORDER_CONFIRMED', 'ORDER_CANCELLED')),

                               CONSTRAINT chk_notifications_channel
                                   CHECK (channel IN ('EMAIL', 'SMS')),

                               CONSTRAINT chk_notifications_status
                                   CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);


-- =====================================================
-- Indexes
-- =====================================================

CREATE INDEX idx_notifications_order_id
    ON notifications(order_id);

CREATE INDEX idx_notifications_status
    ON notifications(status);