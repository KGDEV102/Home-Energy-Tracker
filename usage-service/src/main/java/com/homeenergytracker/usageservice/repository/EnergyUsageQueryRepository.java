package com.homeenergytracker.usageservice.repository;

import com.homeenergytracker.usageservice.dto.DeviceUsageResponse;
import com.homeenergytracker.usageservice.dto.UsageDataPointResponse;
import com.homeenergytracker.usageservice.dto.UsageSummaryResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EnergyUsageQueryRepository {
    UsageSummaryResponse findSummary(
            Long userId,
            Instant from,
            Instant to

    );
    List<DeviceUsageResponse> findUsageByDevice(
            Long userId,
            Instant from,
            Instant to
    );
    List<UsageDataPointResponse> findDeviceTimeSeries(
            Long userId,
            UUID deviceUid,
            Instant from,
            Instant to,
            String window
    );

}
