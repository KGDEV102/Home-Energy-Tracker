-- =========================================================
-- LIGHT
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('Philips', 'Hue White A60', 'LIGHT', NULL, 'ACTIVE'),
    ('Philips', 'Hue White and Color Ambiance A60', 'LIGHT', NULL, 'ACTIVE'),
    ('TP-Link', 'Tapo L530E', 'LIGHT', NULL, 'ACTIVE'),
    ('Xiaomi', 'Mi Smart LED Bulb Essential', 'LIGHT', NULL, 'ACTIVE'),
    ('LIFX', 'Color A60', 'LIGHT', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;


-- =========================================================
-- AIR CONDITIONER
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('Daikin', 'FTKB25WAVMV', 'AIR_CONDITIONER', NULL, 'ACTIVE'),
    ('Panasonic', 'CS-XU9ZKH-8', 'AIR_CONDITIONER', NULL, 'ACTIVE'),
    ('LG', 'V10API1', 'AIR_CONDITIONER', NULL, 'ACTIVE'),
    ('Samsung', 'AR10TYHYEWKNSV', 'AIR_CONDITIONER', NULL, 'ACTIVE'),
    ('Mitsubishi Electric', 'MSY-GR25VF', 'AIR_CONDITIONER', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;


-- =========================================================
-- REFRIGERATOR
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('Samsung', 'RB33T307029', 'REFRIGERATOR', NULL, 'ACTIVE'),
    ('LG', 'GBV3200CPY', 'REFRIGERATOR', NULL, 'ACTIVE'),
    ('Panasonic', 'NR-BX410', 'REFRIGERATOR', NULL, 'ACTIVE'),
    ('Bosch', 'KGN36VICT', 'REFRIGERATOR', NULL, 'ACTIVE'),
    ('Electrolux', 'ETB3400K-H', 'REFRIGERATOR', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;


-- =========================================================
-- WASHING MACHINE
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('LG', 'FV1410S4W', 'WASHING_MACHINE', NULL, 'ACTIVE'),
    ('Samsung', 'WW90T634DLN', 'WASHING_MACHINE', NULL, 'ACTIVE'),
    ('Electrolux', 'EWF9024P5WB', 'WASHING_MACHINE', NULL, 'ACTIVE'),
    ('Bosch', 'WGG244A0SG', 'WASHING_MACHINE', NULL, 'ACTIVE'),
    ('Panasonic', 'NA-V10FX2LVT', 'WASHING_MACHINE', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;


-- =========================================================
-- TELEVISION
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('Sony', 'KD-43X75K', 'TELEVISION', NULL, 'ACTIVE'),
    ('Samsung', 'UA55AU7002', 'TELEVISION', NULL, 'ACTIVE'),
    ('LG', '55UQ8000PSC', 'TELEVISION', NULL, 'ACTIVE'),
    ('TCL', '55P635', 'TELEVISION', NULL, 'ACTIVE'),
    ('Panasonic', 'TH-55MX650V', 'TELEVISION', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;


-- =========================================================
-- HEATER
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('Dyson', 'Hot and Cool AM09', 'HEATER', NULL, 'ACTIVE'),
    ('DeLonghi', 'HFX30C18', 'HEATER', NULL, 'ACTIVE'),
    ('Xiaomi', 'Mi Smart Space Heater S', 'HEATER', NULL, 'ACTIVE'),
    ('Philips', 'CX5120-11', 'HEATER', NULL, 'ACTIVE'),
    ('Rowenta', 'SO6510', 'HEATER', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;


-- =========================================================
-- FAN
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('Xiaomi', 'Smart Standing Fan 2', 'FAN', NULL, 'ACTIVE'),
    ('Dyson', 'Pure Cool TP07', 'FAN', NULL, 'ACTIVE'),
    ('Panasonic', 'F-409K', 'FAN', NULL, 'ACTIVE'),
    ('Mitsubishi Electric', 'R16-GV', 'FAN', NULL, 'ACTIVE'),
    ('Senko', 'DR1608', 'FAN', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;


-- =========================================================
-- THERMOSTAT
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('Google', 'Nest Learning Thermostat 3rd Gen', 'THERMOSTAT', NULL, 'ACTIVE'),
    ('Ecobee', 'Smart Thermostat Premium', 'THERMOSTAT', NULL, 'ACTIVE'),
    ('Honeywell', 'Home T9 Smart Thermostat', 'THERMOSTAT', NULL, 'ACTIVE'),
    ('Tado', 'Smart Thermostat V3 Plus', 'THERMOSTAT', NULL, 'ACTIVE'),
    ('Netatmo', 'Smart Thermostat', 'THERMOSTAT', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;


-- =========================================================
-- CAMERA
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('TP-Link', 'Tapo C200', 'CAMERA', NULL, 'ACTIVE'),
    ('Xiaomi', 'Smart Camera C300', 'CAMERA', NULL, 'ACTIVE'),
    ('Google', 'Nest Cam Indoor', 'CAMERA', NULL, 'ACTIVE'),
    ('Eufy', 'Indoor Cam C24', 'CAMERA', NULL, 'ACTIVE'),
    ('Arlo', 'Essential Indoor Camera', 'CAMERA', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;


-- =========================================================
-- SPEAKER
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('Google', 'Nest Mini 2nd Gen', 'SPEAKER', NULL, 'ACTIVE'),
    ('Amazon', 'Echo Dot 5th Gen', 'SPEAKER', NULL, 'ACTIVE'),
    ('Apple', 'HomePod Mini', 'SPEAKER', NULL, 'ACTIVE'),
    ('Sonos', 'One SL', 'SPEAKER', NULL, 'ACTIVE'),
    ('JBL', 'Link Music', 'SPEAKER', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;


-- =========================================================
-- LOCK
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('August', 'Wi-Fi Smart Lock 4th Gen', 'LOCK', NULL, 'ACTIVE'),
    ('Yale', 'Assure Lock 2', 'LOCK', NULL, 'ACTIVE'),
    ('Aqara', 'Smart Lock U100', 'LOCK', NULL, 'ACTIVE'),
    ('Samsung', 'SHP-DP609', 'LOCK', NULL, 'ACTIVE'),
    ('Lockly', 'Vision Elite', 'LOCK', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;


-- =========================================================
-- DOORBELL
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('Google', 'Nest Doorbell Battery', 'DOORBELL', NULL, 'ACTIVE'),
    ('Ring', 'Video Doorbell 4', 'DOORBELL', NULL, 'ACTIVE'),
    ('Eufy', 'Video Doorbell 2K', 'DOORBELL', NULL, 'ACTIVE'),
    ('Aqara', 'Video Doorbell G4', 'DOORBELL', NULL, 'ACTIVE'),
    ('Arlo', 'Essential Video Doorbell', 'DOORBELL', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;


-- =========================================================
-- OTHER / ENERGY MONITORING
-- =========================================================
INSERT INTO device_models
(manufacturer, model_name, type, rated_power_w, status)
VALUES
    ('Shelly', 'Plug S', 'OTHER', NULL, 'ACTIVE'),
    ('TP-Link', 'Tapo P110', 'OTHER', NULL, 'ACTIVE'),
    ('Sonoff', 'POWR3', 'OTHER', NULL, 'ACTIVE'),
    ('Emporia', 'Vue 2 Energy Monitor', 'OTHER', NULL, 'ACTIVE'),
    ('Sense', 'Home Energy Monitor', 'OTHER', NULL, 'ACTIVE')
    ON CONFLICT DO NOTHING;