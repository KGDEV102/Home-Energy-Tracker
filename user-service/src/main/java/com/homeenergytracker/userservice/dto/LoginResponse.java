package com.homeenergytracker.userservice.dto;

public record LoginResponse (
        UserDto user,
        String accessToken,
        String tokenType,
        long expiresIn
){
}
