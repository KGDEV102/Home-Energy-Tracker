package com.homeenergytracker.deviceservice.exception;
import com.homeenergytracker.common.exception.BusinessException;

public class InvalidDeviceRequestException extends BusinessException {
    public InvalidDeviceRequestException(String message) {
        super("INVALID_DEVICE_REQUEST", message);
    }
}
