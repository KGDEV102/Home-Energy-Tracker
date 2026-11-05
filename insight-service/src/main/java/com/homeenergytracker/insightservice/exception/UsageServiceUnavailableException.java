package com.homeenergytracker.insightservice.exception;

import com.homeenergytracker.common.exception.BusinessException;

public class UsageServiceUnavailableException extends BusinessException {

    private static final String CODE = "USAGE_SERVICE_UNAVAILABLE";

    public UsageServiceUnavailableException(String message) {
        super(CODE, message);
    }

    public UsageServiceUnavailableException(String message, Throwable cause) {
        super(CODE, message, cause);
    }
}
