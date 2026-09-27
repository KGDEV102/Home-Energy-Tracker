package com.homeenergytracker.deviceservice.repository;

import com.homeenergytracker.deviceservice.entity.DeviceModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface    DeviceModelRepository extends JpaRepository<DeviceModel,Long> {
    @Query(
            value = """
            SELECT dm.*
            FROM device_models dm
            WHERE dm.status = 'ACTIVE'
              AND (
                  :keyword IS NULL
                  OR concat_ws(
                      ' ',
                      dm.manufacturer,
                      dm.model_name
                  ) ILIKE '%' || :keyword || '%'
              )
              AND (
                  :type IS NULL
                  OR dm.type = :type
              )
            ORDER BY
                CASE
                    WHEN lower(dm.model_name) = lower(:keyword)
                        THEN 1
                    WHEN lower(dm.manufacturer) = lower(:keyword)
                        THEN 2
                    WHEN dm.model_name ILIKE :keyword || '%'
                        THEN 3
                    WHEN dm.manufacturer ILIKE :keyword || '%'
                        THEN 4
                    ELSE 5
                END,
                lower(dm.manufacturer),
                lower(dm.model_name),
                dm.id
            """,
            nativeQuery = true
    )
    Slice<DeviceModel> searchActiveModels(
            @Param("keyword") String keyword,
            @Param("type") String type,
            Pageable pageable
    );
}
