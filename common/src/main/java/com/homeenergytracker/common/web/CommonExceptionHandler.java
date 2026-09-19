package com.homeenergytracker.common.web;

import com.homeenergytracker.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class CommonExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ){
        Map<String,String> details = new LinkedHashMap<>();
        for(var error: ex.getBindingResult().getFieldErrors()){
            details.put(error.getField(),error.getDefaultMessage()!=null?error.getDefaultMessage():"Invalid value");

        }
        var response = ErrorResponse.build(
                status,
                "VALIDATION_FAILED",
                "Validation failed",
                details,
                request
        );
        return new ResponseEntity<Object>(
                response.getBody(), headers, status
        );
    }
    // Các lỗi unchecked (spring know and throw): JSON không hợp lệ, sai kiểu tham số, sai http method,...
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String message = status.is5xxServerError()
                ? "An unexpected error occurred"
                : "Request could not be processed";

        var response = ErrorResponse.build(
                status,
                "HTTP_" + status.value(),
                message,
                Map.of(),
                request
        );

        return new ResponseEntity<Object>(
                response.getBody(), headers, status
        );
    }
    // Lỗi còn lại, không dự kiến.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(
            Exception ex,
            WebRequest request
    ) {
        var response = ErrorResponse.build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                Map.of(),
                request
        );

        // Chi tiết chỉ ghi vào log, không trả cho client.
        log.error(
                "Unhandled exception; requestId={}",
                response.getBody().requestId(),
                ex
        );

        return response;
    }
}
