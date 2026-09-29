package com.homeenergytracker.notificationservice.dto;

public record UserNotificationContactResponse(
        Long userId,
        String email
) {
}
