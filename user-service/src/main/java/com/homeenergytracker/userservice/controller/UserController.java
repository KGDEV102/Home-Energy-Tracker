package com.homeenergytracker.userservice.controller;

import com.homeenergytracker.userservice.dto.UpdateUser;
import com.homeenergytracker.userservice.dto.UserDto;
import com.homeenergytracker.userservice.service.UserService;
import jakarta.validation.Valid;
import com.homeenergytracker.common.response.SuccessResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping("/me")
    public SuccessResponse<UserDto> updateCurrentUser(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateUser updateUser
    ) {
        return SuccessResponse.of(userService.updateUser(userId, updateUser));
    }

    @GetMapping("/me")
    public SuccessResponse<UserDto> getCurrentUser(
            @AuthenticationPrincipal Long userId
    ) {
        return SuccessResponse.of(userService.getUserById(userId));
    }

    @DeleteMapping("/me")
    public SuccessResponse<?> deleteCurrentUser(
            @AuthenticationPrincipal Long userId
    ) {
        userService.deleteUserById(userId);
        return SuccessResponse.of();
    }
}
