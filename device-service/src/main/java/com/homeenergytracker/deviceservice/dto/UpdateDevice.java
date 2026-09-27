package com.homeenergytracker.deviceservice.dto;

import com.homeenergytracker.deviceservice.entity.DeviceStatus;
import com.homeenergytracker.deviceservice.entity.DeviceType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateDevice (

        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "customManufacturer must not be blank when provided"
        )
        @Size(
                max = 100,
                message = "customManufacturer must not exceed 100 characters"
        )
        String customManufacturer,

        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "customModelName must not be blank when provided"
        )
        @Size(
                max = 150,
                message = "customModelName must not exceed 150 characters"
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

        @Positive(message = "modelId must be positive")
        Long modelId,

        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "name must not be blank when provided"
        )
        @Size(
                max = 255,
                message = "name must not exceed 255 characters"
        )
        String name,

        DeviceType type,

        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "location must not be blank when provided"
        )
        @Size(
                max = 255,
                message = "location must not exceed 255 characters"
        )
        String location,

        DeviceStatus status

){
}
