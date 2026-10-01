package com.homeenergytracker.usageservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DeviceUsageResponse(
        UUID deviceUid,
        String deviceType,
        BigDecimal totalEnergyWh,
        BigDecimal averagePowerW,
        BigDecimal maxPowerW
) {
}
