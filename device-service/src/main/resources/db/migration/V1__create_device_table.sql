CREATE TABLE device_models(

                              id              BIGSERIAL PRIMARY KEY,
                              manufacturer    VARCHAR(100) NOT NULL,
                              model_name      VARCHAR(150) NOT NULL,
                              type            VARCHAR(50) NOT NULL,
                              rated_power_w   NUMERIC(10,3),
                              status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                              created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT chk_device_models_manufacturer_not_blank
                                  CHECK (btrim(manufacturer) <> ''),

                              CONSTRAINT chk_device_models_model_name_not_blank
                                  CHECK (btrim(model_name) <> ''),

                              CONSTRAINT chk_device_models_type
                                  CHECK (
                                      type IN (
                                               'LIGHT',
                                               'AIR_CONDITIONER',
                                               'REFRIGERATOR',
                                               'WASHING_MACHINE',
                                               'TELEVISION',
                                               'HEATER',
                                               'FAN',
                                               'THERMOSTAT',
                                               'CAMERA',
                                               'SPEAKER',
                                               'LOCK',
                                               'DOORBELL',
                                               'OTHER'
                                          )
                                      ),

                              CONSTRAINT chk_device_models_rated_power
                                  CHECK (
                                      rated_power_w IS NULL
                                          OR rated_power_w > 0
                                      ),

                              CONSTRAINT chk_device_models_status
                                  CHECK (
                                      status IN ('ACTIVE', 'DISCONTINUED')
                                      )

);
CREATE UNIQUE INDEX uq_device_models_manufacturer_model
    ON device_models (
                      lower(manufacturer),
                      lower(model_name)
        );
CREATE TABLE devices(
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL check(user_id > 0),
    device_uid UUID NOT NULL,
    model_id BIGINT REFERENCES device_models(id),
    custom_manufacturer VARCHAR(100),
    custom_model_name VARCHAR(150),




  CONSTRAINT chk_devices_name_not_blank
      CHECK (btrim(name) <> ''),

  CONSTRAINT chk_devices_type
      CHECK (
          type IN (
                   'LIGHT',
                   'AIR_CONDITIONER',
                   'REFRIGERATOR',
                   'WASHING_MACHINE',
                   'TELEVISION',
                   'HEATER',
                   'FAN',
                   'THERMOSTAT',
                   'CAMERA',
                   'SPEAKER',
                   'LOCK',
                   'DOORBELL',
                   'OTHER'
              )
          ),


  name VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL,
  location VARCHAR(255),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' check (status in ('ACTIVE','DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_devices_location_not_blank
      CHECK (
          location IS NULL
              OR btrim(location) <> ''
          ),

  CONSTRAINT chk_devices_custom_manufacturer_not_blank
      CHECK (
          custom_manufacturer IS NULL
              OR btrim(custom_manufacturer) <> ''
          ),
  CONSTRAINT chk_devices_model_source
      CHECK (
          (
              model_id IS NOT NULL
                  AND custom_manufacturer IS NULL
                  AND custom_model_name IS NULL
              )
              OR
          (
              model_id IS NULL
                  AND custom_model_name IS NOT NULL
                  AND btrim(custom_model_name) <> ''
              )
          )


);
CREATE UNIQUE INDEX uq_devices_device_uid
    ON devices (device_uid);

CREATE INDEX idx_devices_model_id
    ON devices (model_id);