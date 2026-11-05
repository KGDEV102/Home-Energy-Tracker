package com.homeenergytracker.notificationservice.exception;

import com.homeenergytracker.common.response.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationExceptionHandler {
    @ExceptionHandler(NotificationNotFound.class)
    public ResponseEntity<ErrorResponse> handleNotificationNotFound(
            NotificationNotFound exception,
            WebRequest request
    ) {
        return ErrorResponse.build(
                HttpStatus.NOT_FOUND,
                exception.getCode(),
                exception.getMessage(),
                Map.of(),
                request
        );
    }
}
