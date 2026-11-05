package com.homeenergytracker.ingestionservice.exception;

public class DeviceServiceUnavailableException extends RuntimeException {

    public DeviceServiceUnavailableException(String message) {
        super(message);
    }

    public DeviceServiceUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
