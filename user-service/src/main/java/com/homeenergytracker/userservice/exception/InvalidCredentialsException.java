package com.homeenergytracker.userservice.exception;

import com.homeenergytracker.common.exception.BusinessException;

public class InvalidCredentialsException extends BusinessException {
    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Email or password is incorrect");
    }
}
