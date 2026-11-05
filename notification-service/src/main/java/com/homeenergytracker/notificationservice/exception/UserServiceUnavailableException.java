package com.homeenergytracker.notificationservice.exception;

public class UserServiceUnavailableException extends IllegalStateException {

    public UserServiceUnavailableException(String message) {
        super(message);
    }

    public UserServiceUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
