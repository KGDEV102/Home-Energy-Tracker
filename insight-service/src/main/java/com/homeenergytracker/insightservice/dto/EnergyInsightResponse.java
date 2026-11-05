package com.homeenergytracker.insightservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EnergyInsightResponse(
        Instant from,
        Instant to,
        BigDecimal totalEnergyWh,
        BigDecimal averagePowerW,
        BigDecimal maxPowerW,
        String summary,
        List<String> observations,
        List<String> recommendations,
        InsightSeverity severity,
        String model
) {
}
