package com.homeenergytracker.userservice.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import com.homeenergytracker.common.response.ErrorResponse;

import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserExceptionHandler {

    @ExceptionHandler(UserNotExists.class)
    public ResponseEntity<ErrorResponse> handleUserNotExists(
            UserNotExists ex,
            WebRequest request
    ) {
        return com.homeenergytracker.common.response.ErrorResponse.build(
                HttpStatus.NOT_FOUND,
                ex.getCode(),
                ex.getMessage(),
                Map.of(),
                request
        );
    }
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex,
            WebRequest request
    ) {
        return com.homeenergytracker.common.response.ErrorResponse.build(
                HttpStatus.CONFLICT,
                ex.getCode(),
                ex.getMessage(),
                Map.of(),
                request
        );
    }


}
