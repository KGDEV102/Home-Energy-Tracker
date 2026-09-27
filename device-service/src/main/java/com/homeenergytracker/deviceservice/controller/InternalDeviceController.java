package com.homeenergytracker.deviceservice.controller;

import com.homeenergytracker.deviceservice.dto.DeviceSimulationTargetResponse;
import com.homeenergytracker.deviceservice.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/devices")
@RequiredArgsConstructor
public class InternalDeviceController {
    private final DeviceService deviceService;

    @GetMapping("/simulation-targets")
    public com.homeenergytracker.common.response.SuccessResponse<
                List<DeviceSimulationTargetResponse>
                > getSimulationTargets() {

        return com.homeenergytracker.common.response.SuccessResponse.of(
                deviceService.getSimulationTargets()
        );
    }
}
