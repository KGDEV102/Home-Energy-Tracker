package com.homeenergytracker.usageservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeviceUsageTimeSeriesResponse(
        UUID deviceUid,
        String deviceType,
        String window,
        Instant from,
        Instant to,
        List<UsageDataPointResponse> points
) {
}
