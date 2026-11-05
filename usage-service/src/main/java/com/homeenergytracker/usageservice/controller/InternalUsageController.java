package com.homeenergytracker.usageservice.controller;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.usageservice.dto.UsageSummaryResponse;
import com.homeenergytracker.usageservice.service.EnergyUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/internal/v1/usages")
@RequiredArgsConstructor
public class InternalUsageController {

    private final EnergyUsageService energyUsageService;

    @GetMapping("/users/{userId}/summary")
    public SuccessResponse<UsageSummaryResponse> getSummary(
            @PathVariable Long userId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "userId must be positive"
            );
        }

        return SuccessResponse.of(
                energyUsageService.getSummary(userId, from, to)
        );
    }
}
