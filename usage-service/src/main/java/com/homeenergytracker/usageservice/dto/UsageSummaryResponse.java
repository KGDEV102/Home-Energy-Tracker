package com.homeenergytracker.usageservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record UsageSummaryResponse(
        BigDecimal totalEnergyWh,
        BigDecimal averagePowerW,
        BigDecimal maxPowerW,
        Instant from,
        Instant to
) {
}
