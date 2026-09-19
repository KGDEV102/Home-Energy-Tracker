package com.homeenergytracker.common.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ErrorResponse (
        boolean success,
        ErrorDetail error,
        int statusCode,
        Instant timestamp,
        String path,
        String requestId
){

    public static ResponseEntity<ErrorResponse> build(
            HttpStatusCode status,
            String code,
            String message,
            Map<String,String> details,
            WebRequest request
    ){
        String requestId = (String)request.getAttribute(
                "requestId",WebRequest.SCOPE_REQUEST
        );
        if(requestId == null){
            requestId = UUID.randomUUID().toString();
            request.setAttribute(
                    "requestId",requestId,WebRequest.SCOPE_REQUEST
            );
        }
        String path = request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : "";
        ErrorResponse body = new ErrorResponse(
                false,
                new ErrorDetail(code, message, details),
                status.value(),
                Instant.now(),
                path,
                requestId
        );
        return ResponseEntity.status(status.value()).body(body);

    }
}
