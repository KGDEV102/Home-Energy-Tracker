package com.homeenergytracker.usageservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record UsageDataPointResponse(
        Instant timestamp,
        BigDecimal energyWh,
        BigDecimal averagePowerW
) {
}
