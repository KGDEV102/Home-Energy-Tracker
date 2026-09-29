package com.homeenergytracker.userservice.controller;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.userservice.dto.UserNotificationContactResponse;
import com.homeenergytracker.userservice.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {
    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}/notification-contact")
    public SuccessResponse<UserNotificationContactResponse>
    getNotificationContact(@PathVariable("userId") Long userId) {
        return SuccessResponse.of(
                userService.getNotificationContact(userId)
        );
    }
}
