package com.homeenergytracker.ingestionservice.client;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.ingestionservice.dto.DeviceSimulationTargetResponse;
import com.homeenergytracker.ingestionservice.exception.DeviceServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Optional;

@Component
public class DeviceSimulationTargetGateway {
    private static final Logger log =
            LoggerFactory.getLogger(DeviceSimulationTargetGateway.class);

    private final DeviceServiceClient deviceServiceClient;
    private final String internalToken;

    public DeviceSimulationTargetGateway(
            DeviceServiceClient deviceServiceClient,
            @Value("${device-service.internal-token}") String internalToken
    ) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalArgumentException(
                    "device-service.internal-token must not be blank"
            );
        }
        this.deviceServiceClient = deviceServiceClient;
        this.internalToken = internalToken;
    }

    @CircuitBreaker(
            name = "deviceService",
            fallbackMethod = "deviceServiceFallback"
    )
    public Optional<List<DeviceSimulationTargetResponse>> loadTargets() {
        try {
            SuccessResponse<List<DeviceSimulationTargetResponse>> response =
                    deviceServiceClient.getSimulationTargets(internalToken);

            if (response == null
                    || !response.success()
                    || response.data() == null) {
                throw new DeviceServiceUnavailableException(
                        "Invalid response from device-service"
                );
            }

            return Optional.of(List.copyOf(response.data()));
        } catch (HttpClientErrorException exception) {
            throw exception;
        } catch (DeviceServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DeviceServiceUnavailableException(
                    "Could not load simulation targets from device-service",
                    exception
            );
        }
    }

    private Optional<List<DeviceSimulationTargetResponse>>
    deviceServiceFallback(Throwable cause) {
        log.warn(
                "Skipping device refresh because device-service is unavailable",
                cause
        );
        return Optional.empty();
    }
}
