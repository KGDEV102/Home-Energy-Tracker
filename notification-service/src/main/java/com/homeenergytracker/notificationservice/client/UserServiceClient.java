package com.homeenergytracker.notificationservice.client;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.notificationservice.dto.UserNotificationContactResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/internal/v1/users")
public interface UserServiceClient {

    @GetExchange("/{userId}/notification-contact")
    SuccessResponse<UserNotificationContactResponse> getNotificationContact(
            @PathVariable("userId") Long userId,
            @RequestHeader("X-Internal-Token") String internalToken
    );
}
