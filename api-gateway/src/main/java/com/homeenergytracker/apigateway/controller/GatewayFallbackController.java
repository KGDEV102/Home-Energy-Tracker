package com.homeenergytracker.apigateway.controller;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayFallbackController {

    @RequestMapping("/fallback/{service}")
    public ResponseEntity<GatewayFallbackResponse> serviceUnavailable(
            @PathVariable String service,
            HttpServletRequest request
    ) {
        if (request.getDispatcherType() != DispatcherType.FORWARD) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new GatewayFallbackResponse(
                            false,
                            "Fallback endpoint is not directly accessible"
                    ));
        }

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new GatewayFallbackResponse(
                        false,
                        service + " is temporarily unavailable"
                ));
    }

    public record GatewayFallbackResponse(
            boolean success,
            String message
    ) {
    }
}
