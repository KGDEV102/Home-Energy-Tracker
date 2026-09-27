package com.homeenergytracker.deviceservice.dto;

import com.homeenergytracker.deviceservice.entity.DeviceStatus;
import com.homeenergytracker.deviceservice.entity.DeviceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
    Long id,
    UUID deviceUid,
    Long userId,
    String name,
    DeviceType type,
    String customModelName,
    String customManufacturer,
    BigDecimal customRatedPowerW,
    String location,
    DeviceStatus status,
    DeviceModelDto model,
    Instant createdAt,
    Instant updatedAt
) {
}
