package com.homeenergytracker.userservice.dto;

public record UserNotificationContactResponse(
        Long userId,
        String email
) {
}
