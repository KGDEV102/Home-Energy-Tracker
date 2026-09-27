package com.homeenergytracker.ingestionservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryReading (
        UUID readingId,
        UUID deviceUid,
        BigDecimal averagePowerW,
        BigDecimal intervalEnergyWh,
        Instant measuredAt
){
}
