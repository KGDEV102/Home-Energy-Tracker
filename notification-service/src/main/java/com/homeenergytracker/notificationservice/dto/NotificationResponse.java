package com.homeenergytracker.notificationservice.dto;

import com.homeenergytracker.notificationservice.entity.NotificationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        Long id,
        UUID deviceUid,
        NotificationType type,
        String title,
        String message,
        BigDecimal triggerValue,
        BigDecimal thresholdValue,
        Instant measuredAt,
        Instant createdAt,
        Instant readAt,
        boolean read
) {
}
