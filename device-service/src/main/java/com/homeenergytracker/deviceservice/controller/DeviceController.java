package com.homeenergytracker.deviceservice.controller;

import com.homeenergytracker.common.security.JwtUserIdExtractor;
import com.homeenergytracker.deviceservice.dto.CreateDevice;
import com.homeenergytracker.deviceservice.dto.DeviceResponse;
import com.homeenergytracker.deviceservice.dto.PageResponse;
import com.homeenergytracker.deviceservice.dto.UpdateDevice;
import com.homeenergytracker.deviceservice.entity.DeviceStatus;
import com.homeenergytracker.deviceservice.service.DeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.homeenergytracker.common.response.SuccessResponse;

@RestController
@RequestMapping("/api/v1/device")
@RequiredArgsConstructor
@Validated
public class DeviceController {


    private final DeviceService deviceService;
    @PostMapping
    public SuccessResponse<DeviceResponse> createDevice(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateDevice device
    ) {
       return SuccessResponse.of(deviceService.createDevice(JwtUserIdExtractor.extract(jwt), device));
    }
    @PatchMapping("/{id}")
    public SuccessResponse<DeviceResponse> updateDevice(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateDevice device,
            @PathVariable Long id
    ) {
        return SuccessResponse.of(deviceService.updateDevice(JwtUserIdExtractor.extract(jwt), id, device));
    }

    @GetMapping("/{id}")
    public SuccessResponse<DeviceResponse> getDevice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        return SuccessResponse.of(deviceService.getById(JwtUserIdExtractor.extract(jwt), id));

    }
    @GetMapping()
    public SuccessResponse<PageResponse<DeviceResponse>> getAllDevicesOfUser(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam DeviceStatus deviceStatus,
               @Min(
                    value = 0,
                    message = "page must be greater than or equal to 0"
            )
            @RequestParam int page,
            @Min(
                    value = 1,
                    message = "size must be greater than or equal to 1"
            )
            @Max(
                    value = 20,
                    message = "size must not exceed 20"
            )
            @RequestParam int size
    ) {
        return SuccessResponse.of(deviceService.getAllDevicesOfUser(
                JwtUserIdExtractor.extract(jwt), deviceStatus, page, size));
    }
    @DeleteMapping("/{id}")
    public SuccessResponse<?> deleteDevice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
         deviceService.deleteDevice(JwtUserIdExtractor.extract(jwt), id);
         return  SuccessResponse.of(null,"Device"+id+" has been deleted");
    }



}
