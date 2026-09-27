package com.homeenergytracker.deviceservice.controller;

import com.homeenergytracker.deviceservice.dto.CreateDevice;
import com.homeenergytracker.deviceservice.dto.DeviceResponse;
import com.homeenergytracker.deviceservice.dto.PageResponse;
import com.homeenergytracker.deviceservice.dto.UpdateDevice;
import com.homeenergytracker.deviceservice.entity.Device;
import com.homeenergytracker.deviceservice.entity.DeviceStatus;
import com.homeenergytracker.deviceservice.service.DeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.homeenergytracker.common.response.SuccessResponse;

import java.awt.print.Pageable;
import java.util.List;

@RestController
@RequestMapping("/api/v1/device")
@RequiredArgsConstructor
@Validated
public class DeviceController {


    private final DeviceService deviceService;
    @PostMapping
    public SuccessResponse<DeviceResponse> createDevice(@Valid @RequestBody CreateDevice device) {
       return SuccessResponse.of( deviceService.createDevice(device));
    }
    @PatchMapping("/{id}")
    public SuccessResponse<DeviceResponse> updateDevice(@Valid @RequestBody UpdateDevice device, @PathVariable Long id) {
        return SuccessResponse.of(deviceService.updateDevice(id,device));
    }

    @GetMapping("/{id}")
    public SuccessResponse<DeviceResponse> getDevice(@PathVariable Long id) {
        return SuccessResponse.of(deviceService.getById(id));

    }
    @GetMapping()
    public SuccessResponse<PageResponse<DeviceResponse>> getAllDevicesOfUser(
            @Positive(message = "userId must be positive")
            @RequestParam Long userId,

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
        return SuccessResponse.of(deviceService.getAllDevicesOfUser(userId,deviceStatus,page,size));
    }
    @DeleteMapping("/{id}")
    public SuccessResponse<?> deleteDevice(@PathVariable Long id) {
         deviceService.deleteDevice(id);
         return  SuccessResponse.of(null,"Device"+id+" has been deleted");
    }



}
