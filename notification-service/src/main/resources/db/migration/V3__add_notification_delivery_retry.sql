ALTER TABLE notifications
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

ALTER TABLE notifications
    DROP CONSTRAINT chk_notifications_delivery_status;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_delivery_status
        CHECK (delivery_status IN ('PENDING', 'RETRYING', 'SENT', 'FAILED'));

UPDATE notifications
SET next_attempt_at = created_at
WHERE delivery_status = 'PENDING'
  AND next_attempt_at IS NULL;

DROP INDEX IF EXISTS idx_notifications_delivery_pending;

CREATE INDEX idx_notifications_delivery_due
    ON notifications (next_attempt_at)
    WHERE delivery_status IN ('PENDING', 'RETRYING');
