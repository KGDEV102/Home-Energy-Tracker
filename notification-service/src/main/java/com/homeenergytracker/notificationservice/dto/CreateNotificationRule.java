package com.homeenergytracker.notificationservice.dto;

import com.homeenergytracker.notificationservice.entity.NotificationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateNotificationRule(
        @NotNull(message = "device_uid is required")
        UUID deviceUid,

        @NotNull(message = "type is required")
        NotificationType type,

        @NotNull(message = "threshold is required")
        @Positive(message = "threshold must be positive")
        BigDecimal threshold,

        @NotNull(message = "cooldown_seconds is required")
        @PositiveOrZero(message = "cooldown_seconds must be zero or positive")
        Long cooldownSeconds,

        Boolean enabled
) {
}
