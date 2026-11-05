package com.homeenergytracker.userservice.exception;

import com.homeenergytracker.common.exception.BusinessException;

public class InvalidRefreshTokenException extends BusinessException {
    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
    }
}
