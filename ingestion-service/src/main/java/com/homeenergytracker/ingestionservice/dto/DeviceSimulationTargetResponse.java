package com.homeenergytracker.ingestionservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DeviceSimulationTargetResponse (
        UUID deviceUid,
        Long userId,
        String deviceType,
        BigDecimal ratedPowerW
){
}
