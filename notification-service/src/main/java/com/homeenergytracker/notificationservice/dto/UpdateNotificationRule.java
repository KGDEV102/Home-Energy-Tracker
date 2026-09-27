package com.homeenergytracker.notificationservice.dto;

import com.homeenergytracker.notificationservice.entity.NotificationType;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateNotificationRule(
        NotificationType type,

        @Positive(message = "threshold must be positive")
        BigDecimal threshold,

        @PositiveOrZero(
                message = "cooldown_seconds must be zero or positive"
        )
        Long cooldownSeconds,

        Boolean enabled
) {
}
