ALTER TABLE devices
    ADD COLUMN custom_rated_power_w NUMERIC(10, 3);

ALTER TABLE devices
    ADD CONSTRAINT chk_devices_custom_rated_power
        CHECK (
            (
                model_id IS NOT NULL
                AND custom_rated_power_w IS NULL
            )
            OR
            (
                model_id IS NULL
                AND (
                    custom_rated_power_w IS NULL
                    OR custom_rated_power_w > 0
                )
            )
        );
