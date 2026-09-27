package com.homeenergytracker.deviceservice.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.homeenergytracker.common.response.ErrorResponse;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@RestControllerAdvice
@Order(value = Ordered.HIGHEST_PRECEDENCE)
public class DeviceExceptionHandler {
    @ExceptionHandler(DeviceModelNotExist.class)
    public ResponseEntity<ErrorResponse> deviceModelNotExist(DeviceModelNotExist ex,
                                                             WebRequest request) {
        return ErrorResponse.build(
                HttpStatus.NOT_FOUND,
                ex.getCode(),
                ex.getMessage(),
                Map.of(),
                request
        );
    }
    @ExceptionHandler(DeviceNotExist.class)
    public ResponseEntity<ErrorResponse> deviceNotExist(
            DeviceNotExist ex,
            WebRequest request
    ){
        return ErrorResponse.build(
                HttpStatus.NOT_FOUND,
                ex.getCode(),
                ex.getMessage(),
                Map.of(),
                request
        );
    }
    @ExceptionHandler(InvalidDeviceRequestException.class)
    public ResponseEntity<ErrorResponse> invalidDeviceRequest(
            InvalidDeviceRequestException ex,
            WebRequest request
    ){
        return ErrorResponse.build(
                HttpStatus.BAD_REQUEST,
                ex.getCode(),
                ex.getMessage(),
                Map.of(),
                request

        );
    }
}
