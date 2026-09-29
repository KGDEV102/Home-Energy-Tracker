package com.homeenergytracker.notificationservice.service;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.notificationservice.client.UserServiceClient;
import com.homeenergytracker.notificationservice.dto.UserNotificationContactResponse;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UserContactService {
    private final UserServiceClient userServiceClient;

    public UserContactService(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    public UserNotificationContactResponse getNotificationContact(
            Long userId
    ) {
        SuccessResponse<UserNotificationContactResponse> response =
                userServiceClient.getNotificationContact(userId);

        if (response == null
                || !response.success()
                || response.data() == null
                || !Objects.equals(response.data().userId(), userId)
                || response.data().email() == null
                || response.data().email().isBlank()) {
            throw new IllegalStateException(
                    "Could not load notification contact for user " + userId
            );
        }

        return response.data();
    }
}
