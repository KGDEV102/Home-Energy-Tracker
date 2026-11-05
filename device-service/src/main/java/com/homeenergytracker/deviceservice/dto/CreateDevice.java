package com.homeenergytracker.deviceservice.dto;

import com.homeenergytracker.deviceservice.entity.DeviceStatus;
import com.homeenergytracker.deviceservice.entity.DeviceType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateDevice(
        @Size(
                max = 100,
                message = "customManufacturer must not exceed 100 characters"
        )
        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "customManufacturer must not be blank when provided"
        )
        String customManufacturer,
        @Size(
                max = 150,
                message = "customModelName must not exceed 150 characters"
        )
        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "customModelName must not be blank when provided"
        )

        String customModelName,
        @DecimalMin(
                value = "0.001",
                message = "customRatedPowerW must be greater than 0"
        )
        @Digits(
                integer = 7,
                fraction = 3,
                message = "customRatedPowerW must have at most 7 integer digits and 3 decimal places"
        )
        BigDecimal customRatedPowerW,
        @Positive(message = "model_id must be positive")
        Long modelId,
        // Kept for backward-compatible JSON only. The server always uses JWT subject as owner.
        Long userId,
        @NotBlank(message = "name is required")
        @Size(
                max = 255,
                message = "name must not exceed 255 characters"
        )
        String name,
        DeviceType type,
        @Size(
                max = 255,
                message = "location must not exceed 255 characters"
        )
        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "location must not be blank when provided"
        )
        String location,
        DeviceStatus status

){
}
