package com.homeenergytracker.userservice.dto;

public record RefreshTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
