package com.homeenergytracker.notificationservice.service;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.notificationservice.client.UserServiceClient;
import com.homeenergytracker.notificationservice.dto.UserNotificationContactResponse;
import com.homeenergytracker.notificationservice.exception.UserServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Objects;

@Service
public class UserContactService {
    private final UserServiceClient userServiceClient;
    private final String internalToken;

    public UserContactService(
            UserServiceClient userServiceClient,
            @Value("${internal.user-service-token}") String internalToken
    ) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalArgumentException(
                    "internal.user-service-token must not be blank"
            );
        }
        this.userServiceClient = userServiceClient;
        this.internalToken = internalToken;
    }

    @CircuitBreaker(
            name = "userService",
            fallbackMethod = "userServiceFallback"
    )
    public UserNotificationContactResponse getNotificationContact(
            Long userId
    ) {
        try {
            SuccessResponse<UserNotificationContactResponse> response =
                    userServiceClient.getNotificationContact(
                            userId,
                            internalToken
                    );

            if (response == null
                    || !response.success()
                    || response.data() == null
                    || !Objects.equals(response.data().userId(), userId)
                    || response.data().email() == null
                    || response.data().email().isBlank()) {
                throw invalidContact(userId);
            }

            return response.data();
        } catch (HttpClientErrorException exception) {
            throw exception;
        } catch (UserServiceUnavailableException exception) {
            throw exception;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(userId, exception);
        }
    }

    private UserNotificationContactResponse userServiceFallback(
            Long userId,
            Throwable cause
    ) {
        if (cause instanceof HttpClientErrorException exception) {
            throw exception;
        }
        if (cause instanceof UserServiceUnavailableException exception) {
            throw exception;
        }
        if (cause instanceof IllegalStateException exception) {
            throw exception;
        }

        throw unavailable(userId, cause);
    }

    private IllegalStateException invalidContact(Long userId) {
        return new IllegalStateException(
                "Could not load notification contact for user " + userId
        );
    }

    private UserServiceUnavailableException unavailable(
            Long userId,
            Throwable cause
    ) {
        String message =
                "Could not load notification contact for user " + userId;

        if (cause == null) {
            return new UserServiceUnavailableException(message);
        }

        return new UserServiceUnavailableException(message, cause);
    }
}
