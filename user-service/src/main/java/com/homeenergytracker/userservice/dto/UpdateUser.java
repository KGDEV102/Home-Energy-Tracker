package com.homeenergytracker.userservice.dto;

import com.homeenergytracker.userservice.entity.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUser (
        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "firstName must not be blank when provided"
        )
        @Size(max = 100,message = "firstName must not exceed 100 characters")
        String firstName,
        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "lastName must not be blank when provided"
        )
        @Size(max = 100,message = "lastName must not exceed 100 characters")
        String lastName,
        @Email(message = "email must be in the correct format")
        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "email must not be blank when provided"
        )
        @Size(max = 255, message = "email must not exceed 255 characters")
        String email,
        String address,
        UserStatus status
){
}
