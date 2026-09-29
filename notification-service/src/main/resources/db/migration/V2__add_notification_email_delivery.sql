ALTER TABLE notifications
    ADD COLUMN recipient_email VARCHAR(255),
    ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN delivery_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN sent_at TIMESTAMPTZ,
    ADD COLUMN last_delivery_error TEXT;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_delivery_status
        CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED')),
    ADD CONSTRAINT chk_notifications_delivery_attempts
        CHECK (delivery_attempts >= 0);

CREATE INDEX idx_notifications_delivery_pending
    ON notifications (created_at)
    WHERE delivery_status IN ('PENDING', 'FAILED');
