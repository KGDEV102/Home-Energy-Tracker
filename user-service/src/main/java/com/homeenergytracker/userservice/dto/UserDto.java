package com.homeenergytracker.userservice.dto;

import com.homeenergytracker.userservice.entity.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UserDto(
        Long id,
        @NotBlank(message = "first_name is required")
        @Size(max = 100,message = "firstName must not exceed 100 characters")
        String firstName,
        @NotBlank(message = "last_name is required")
        @Size(max = 100,message = "lastName must not exceed 100 characters")
        String lastName,
        @Email(message = "email must be in the correct format")
        @NotBlank(message = "email is required")
        @Size(max = 255, message = "email must not exceed 255 characters")
        String email,
        String address,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
