package com.homeenergytracker.insightservice.exception;

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
public class InsightExceptionHandler {

    @ExceptionHandler(AiInsightGenerationException.class)
    public ResponseEntity<ErrorResponse> handleAiInsightGeneration(
            AiInsightGenerationException exception,
            WebRequest request
    ) {
        return ErrorResponse.build(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getCode(),
                exception.getMessage(),
                Map.of(),
                request
        );
    }

    @ExceptionHandler(UsageServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUsageServiceUnavailable(
            UsageServiceUnavailableException exception,
            WebRequest request
    ) {
        return ErrorResponse.build(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getCode(),
                exception.getMessage(),
                Map.of(),
                request
        );
    }
}
