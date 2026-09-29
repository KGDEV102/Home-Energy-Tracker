package com.homeenergytracker.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EnergyUsageEvent(
        UUID eventId,
        int schemaVersion,
        UUID deviceUid,
        Long userId,
        String deviceType,
        BigDecimal averagePowerW,
        BigDecimal intervalEnergyWh,
        Instant measuredAt,
        Instant receivedAt,
        ReadingSource source
) {
}
