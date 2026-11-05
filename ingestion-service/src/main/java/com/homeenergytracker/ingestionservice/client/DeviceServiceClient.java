package com.homeenergytracker.ingestionservice.client;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.ingestionservice.dto.DeviceSimulationTargetResponse;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@HttpExchange("/internal/v1/devices")
public interface DeviceServiceClient {
    @GetExchange("/simulation-targets")
    SuccessResponse<List<DeviceSimulationTargetResponse>>
    getSimulationTargets(
            @RequestHeader("X-Internal-Service-Token") String serviceToken
    );
}
