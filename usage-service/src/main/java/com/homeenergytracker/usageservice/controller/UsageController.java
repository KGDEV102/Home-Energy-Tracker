package com.homeenergytracker.usageservice.controller;

import com.homeenergytracker.usageservice.dto.DeviceUsageResponse;
import com.homeenergytracker.usageservice.dto.UsageDataPointResponse;
import com.homeenergytracker.usageservice.dto.UsageSummaryResponse;
import com.homeenergytracker.usageservice.security.JwtUserIdExtractor;
import com.homeenergytracker.usageservice.service.EnergyUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.homeenergytracker.common.response.SuccessResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/usages")
@RequiredArgsConstructor
public class UsageController {

    private final EnergyUsageService energyUsageService;
    private final JwtUserIdExtractor jwtUserIdExtractor;

    @GetMapping("/summary")
    public SuccessResponse<UsageSummaryResponse> getSummary(
            @AuthenticationPrincipal Jwt jwt,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        Long userId = jwtUserIdExtractor.extract(jwt);

        UsageSummaryResponse response =
                energyUsageService.getSummary(
                        userId,
                        from,
                        to
                );

        return SuccessResponse.of(response);
    }
    @GetMapping("/devices")
    public SuccessResponse<List<DeviceUsageResponse>> getUsageByDevice(
            @AuthenticationPrincipal Jwt jwt,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        Long userId = jwtUserIdExtractor.extract(jwt);

        List<DeviceUsageResponse> response =
                energyUsageService.getUsageByDevice(
                        userId,
                        from,
                        to
                );

        return SuccessResponse.of(response);
    }

    @GetMapping("/devices/{deviceUid}/timeseries")
    public SuccessResponse<List<UsageDataPointResponse>> getDeviceTimeSeries(
            @AuthenticationPrincipal Jwt jwt,

            @PathVariable UUID deviceUid,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(defaultValue = "1h")
            String window
    ) {
        Long userId = jwtUserIdExtractor.extract(jwt);

        List<UsageDataPointResponse> response =
                energyUsageService.getDeviceTimeSeries(
                        userId,
                        deviceUid,
                        from,
                        to,
                        window
                );

        return SuccessResponse.of(response);
    }

}
