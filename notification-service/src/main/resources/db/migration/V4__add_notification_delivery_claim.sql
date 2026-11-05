ALTER TABLE notifications
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN claimed_by VARCHAR(100);

ALTER TABLE notifications
    DROP CONSTRAINT chk_notifications_delivery_status;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_delivery_status
        CHECK (delivery_status IN (
            'PENDING',
            'RETRYING',
            'PROCESSING',
            'SENT',
            'FAILED'
        )),
    ADD CONSTRAINT chk_notifications_delivery_claim
        CHECK (
            (
                delivery_status = 'PROCESSING'
                AND claimed_at IS NOT NULL
                AND locked_until IS NOT NULL
                AND claimed_by IS NOT NULL
            )
            OR
            (
                delivery_status <> 'PROCESSING'
                AND claimed_at IS NULL
                AND locked_until IS NULL
                AND claimed_by IS NULL
            )
        );

CREATE INDEX idx_notifications_delivery_processing_lease
    ON notifications (locked_until, id)
    WHERE delivery_status = 'PROCESSING';
