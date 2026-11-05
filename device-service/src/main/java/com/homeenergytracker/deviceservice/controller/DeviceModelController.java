package com.homeenergytracker.deviceservice.controller;

import com.homeenergytracker.deviceservice.dto.DeviceModelDto;
import com.homeenergytracker.deviceservice.dto.SliceResponse;
import com.homeenergytracker.deviceservice.entity.DeviceModel;
import com.homeenergytracker.deviceservice.entity.DeviceType;
import com.homeenergytracker.deviceservice.service.DeviceModelService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.homeenergytracker.common.response.SuccessResponse;

@RestController
@RequestMapping("/api/v1/device-models")
@Validated
public class DeviceModelController {
    private final DeviceModelService deviceModelService;
    public DeviceModelController(DeviceModelService deviceModelService) {
        this.deviceModelService = deviceModelService;
    }
    @GetMapping
    @Operation(security = {})
    public SuccessResponse<SliceResponse<DeviceModelDto>> searchModels(
            @RequestParam(required = false)
            @Size(
                    max = 100,
                    message = "keyword must not exceed 100 characters"
            )
            String keyword,
            @RequestParam(required = false)
            DeviceType type,
            @RequestParam(defaultValue = "0")
            @Min(
                    value = 0,
                    message = "page must be greater than or equal to 0"
            )
            int page,
            @RequestParam(defaultValue = "10")
            @Min(
                    value = 1,
                    message = "size must be greater than or equal to 1"
            )
            @Max(
                    value = 20,
                    message = "size must not exceed 20"
            )
            int size

            ){
        SliceResponse<DeviceModelDto> result =
                deviceModelService.searchModels(
                        keyword,
                        type,
                        page,
                        size
                );

        return SuccessResponse.of(
                result,
                "Device models retrieved successfully"
        );

    }

}
