package com.homeenergytracker.usageservice.service;

import com.homeenergytracker.usageservice.dto.DeviceUsageResponse;
import com.homeenergytracker.usageservice.dto.UsageDataPointResponse;
import com.homeenergytracker.usageservice.dto.UsageSummaryResponse;
import com.homeenergytracker.usageservice.repository.EnergyUsageQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnergyUsageService {

    private static final Set<String> SUPPORTED_WINDOWS =
            Set.of("5m", "15m", "1h", "1d");

    private static final Duration MAX_QUERY_RANGE =
            Duration.ofDays(366);

    private final EnergyUsageQueryRepository queryRepository;
    private void validateTimeRange(
            Instant from,
            Instant to
    ) {
        if (from == null || to == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "from and to are required"
            );
        }

        if (!from.isBefore(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "from must be before to"
            );
        }

        Duration queryRange = Duration.between(from, to);

        if (queryRange.compareTo(MAX_QUERY_RANGE) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Query range must not exceed 366 days"
            );
        }
    }

    private void validateWindow(String window) {
        if (window == null || !SUPPORTED_WINDOWS.contains(window)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "window must be one of: 5m, 15m, 1h, 1d"
            );
        }
    }

    public UsageSummaryResponse getSummary(
            Long userId,
            Instant from,
            Instant to
    ) {
        validateTimeRange(from, to);

        return queryRepository.findSummary(
                userId,
                from,
                to
        );
    }

    public List<DeviceUsageResponse> getUsageByDevice(
            Long userId,
            Instant from,
            Instant to
    ) {
        validateTimeRange(from, to);

        return queryRepository.findUsageByDevice(
                userId,
                from,
                to
        );
    }

    public List<UsageDataPointResponse> getDeviceTimeSeries(
            Long userId,
            UUID deviceUid,
            Instant from,
            Instant to,
            String window
    ) {
        validateTimeRange(from, to);
        validateWindow(window);

        if (deviceUid == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "deviceUid is required"
            );
        }

        return queryRepository.findDeviceTimeSeries(
                userId,
                deviceUid,
                from,
                to,
                window
        );
    }

}
