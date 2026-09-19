package com.homeenergytracker.userservice.exception;
import  com.homeenergytracker.common.exception.BusinessException;

public class EmailAlreadyExistsException extends BusinessException{
    public EmailAlreadyExistsException() {
        super("EMAIL_ALREADY_EXISTS", "Email already exists");
    }
}
