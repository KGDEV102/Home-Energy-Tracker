CREATE TABLE notification_rules (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT         NOT NULL,
    device_uid          UUID           NOT NULL,
    type                VARCHAR(50)    NOT NULL,
    threshold           NUMERIC(12, 3) NOT NULL,
    cooldown_seconds    BIGINT         NOT NULL DEFAULT 0,
    last_triggered_at   TIMESTAMPTZ,
    enabled             BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_notification_rules_type
        CHECK (type IN (
            'POWER_THRESHOLD',
            'DAILY_ENERGY_LIMIT',
            'DEVICE_OFFLINE',
            'UNUSUAL_USAGE'
        )),

    CONSTRAINT chk_notification_rules_user_id
        CHECK (user_id > 0),

    CONSTRAINT chk_notification_rules_threshold
        CHECK (threshold > 0),

    CONSTRAINT chk_notification_rules_cooldown
        CHECK (cooldown_seconds >= 0),

    CONSTRAINT uq_notification_rules_user_device_type
        UNIQUE (user_id, device_uid, type)
);

CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            UUID           NOT NULL,
    rule_id             BIGINT         NOT NULL,
    user_id             BIGINT         NOT NULL,
    device_uid          UUID           NOT NULL,
    type                VARCHAR(50)    NOT NULL,
    title               VARCHAR(255)   NOT NULL,
    message             TEXT           NOT NULL,
    trigger_value       NUMERIC(12, 3) NOT NULL,
    threshold_value     NUMERIC(12, 3) NOT NULL,
    measured_at         TIMESTAMPTZ    NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at             TIMESTAMPTZ,

    CONSTRAINT fk_notifications_rule
        FOREIGN KEY (rule_id)
        REFERENCES notification_rules(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_notifications_user_id
        CHECK (user_id > 0),

    CONSTRAINT chk_notifications_type
        CHECK (type IN (
            'POWER_THRESHOLD',
            'DAILY_ENERGY_LIMIT',
            'DEVICE_OFFLINE',
            'UNUSUAL_USAGE'
        )),

    CONSTRAINT chk_notifications_title_not_blank
        CHECK (btrim(title) <> ''),

    CONSTRAINT chk_notifications_message_not_blank
        CHECK (btrim(message) <> ''),

    CONSTRAINT chk_notifications_trigger_value
        CHECK (trigger_value >= 0),

    CONSTRAINT chk_notifications_threshold_value
        CHECK (threshold_value > 0),

    CONSTRAINT uq_notifications_event_rule
        UNIQUE (event_id, rule_id)
);

CREATE INDEX idx_notification_rules_device_enabled
    ON notification_rules (device_uid, enabled);

CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at DESC);

CREATE INDEX idx_notifications_user_unread
    ON notifications (user_id, created_at DESC)
    WHERE read_at IS NULL;
