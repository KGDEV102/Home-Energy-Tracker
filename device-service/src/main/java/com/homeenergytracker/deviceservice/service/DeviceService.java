package com.homeenergytracker.deviceservice.service;

import com.homeenergytracker.deviceservice.dto.*;
import com.homeenergytracker.deviceservice.entity.Device;
import com.homeenergytracker.deviceservice.entity.DeviceModel;
import com.homeenergytracker.deviceservice.entity.DeviceModelStatus;
import com.homeenergytracker.deviceservice.entity.DeviceStatus;
import com.homeenergytracker.deviceservice.exception.DeviceModelNotExist;
import com.homeenergytracker.deviceservice.exception.DeviceNotExist;
import com.homeenergytracker.deviceservice.exception.InvalidDeviceRequestException;
import com.homeenergytracker.deviceservice.mapper.DeviceMapper;
import com.homeenergytracker.deviceservice.repository.DeviceModelRepository;
import com.homeenergytracker.deviceservice.repository.DeviceRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DeviceService {
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 20;
    private final DeviceMapper deviceMapper;
    private final DeviceRepository deviceRepository;
    private final DeviceModelRepository deviceModelRepository;
    public DeviceService(DeviceMapper deviceMapper, DeviceRepository deviceRepository, DeviceModelRepository deviceModelRepository) {
        this.deviceMapper = deviceMapper;
        this.deviceRepository = deviceRepository;
        this.deviceModelRepository = deviceModelRepository;
    }
    public DeviceResponse createDevice(CreateDevice request){
        Device device = new Device();


        device.setUserId(request.userId());
        device.setName(request.name());
        device.setType(request.type());
        device.setLocation(request.location());
        device.setStatus(request.status());

        if (request.modelId() != null) {
            DeviceModel model = deviceModelRepository
                    .findById(request.modelId())
                    .orElseThrow(DeviceModelNotExist::new
                    );
            if (model.getStatus() != DeviceModelStatus.ACTIVE) {
                throw new InvalidDeviceRequestException(
                        "Device model is not active"
                );
            }
            if(request.customModelName() != null
                    || request.customManufacturer() != null
                    || request.customRatedPowerW() != null
                    || request.type() != null){
                throw new InvalidDeviceRequestException(
                        "customModelName, customManufacturer, customRatedPowerW and type must not be provided when modelId is present"
                );

            }

            device.setModel(model);
            device.setType(model.getType());
        } else {
            if (request.customModelName() == null
                    || request.customModelName().isBlank()
                    || request.customRatedPowerW() == null
                    || request.customRatedPowerW().signum() <= 0
                    || request.type() == null) {
                throw new InvalidDeviceRequestException(
                        "Custom device requires customModelName, customRatedPowerW greater than 0 and type"
                );
            }
            device.setCustomManufacturer(request.customManufacturer());
            device.setCustomModelName(request.customModelName());
            device.setCustomRatedPowerW(request.customRatedPowerW());
            device.setType(request.type());

        }

        deviceRepository.save(device);
        return deviceMapper.toDeviceResponse(device);
    }
    @Transactional
    public DeviceResponse updateDevice(Long id, UpdateDevice request){
        Device device = deviceRepository.findById(id).orElseThrow(DeviceNotExist::new);
        if (request.modelId() != null) {
            // Chuyển sang hoặc thay đổi catalog model
            if (request.customModelName() != null
                    || request.customManufacturer() != null
                    || request.customRatedPowerW() != null
                    || request.type() != null) {
                throw new InvalidDeviceRequestException(
                        "Custom fields, customRatedPowerW and type must not be provided with modelId"
                );
            }

            DeviceModel model = deviceModelRepository
                    .findById(request.modelId())
                    .orElseThrow(DeviceModelNotExist::new);

            if (model.getStatus() != DeviceModelStatus.ACTIVE) {
                throw new InvalidDeviceRequestException(
                        "Device model is not active"
                );
            }

            device.setModel(model);
            device.setType(model.getType());
            device.setCustomModelName(null);
            device.setCustomManufacturer(null);
            device.setCustomRatedPowerW(null);

        } else if (device.getModel() == null) {
            // Thiết bị hiện tại là custom: chỉ cập nhật field được gửi lên
            if (request.customModelName() != null) {
                device.setCustomModelName(request.customModelName());
            }

            if (request.customManufacturer() != null) {
                device.setCustomManufacturer(request.customManufacturer());
            }

            if (request.customRatedPowerW() != null) {
                if (request.customRatedPowerW().signum() <= 0) {
                    throw new InvalidDeviceRequestException(
                            "customRatedPowerW must be greater than 0"
                    );
                }
                device.setCustomRatedPowerW(request.customRatedPowerW());
            }

            if (request.type() != null) {
                device.setType(request.type());
            }

        } else {
            // Thiết bị hiện tại là catalog
            if (request.customModelName() != null
                    || request.customManufacturer() != null
                    || request.customRatedPowerW() != null
                    || request.type() != null) {
                throw new InvalidDeviceRequestException(
                        "Custom fields, customRatedPowerW and type cannot be updated for a catalog device"
                );
            }
        }

         deviceMapper.updateEntity(request, device);
        deviceRepository.save(device);

        return deviceMapper.toDeviceResponse(device);


    }
    @Transactional(readOnly = true)
    public DeviceResponse getById(Long id) {
        Device device = deviceRepository.findById(id).orElseThrow(DeviceNotExist::new);
        return deviceMapper.toDeviceResponse(device);
    }
    public PageResponse<DeviceResponse> getAllDevicesOfUser(Long userId, DeviceStatus status, Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize
        );
        Page<Device> result = deviceRepository.findAllByUserIdAndStatus(userId,status,pageable);
        List<DeviceResponse> items = result.stream().map(deviceMapper::toDeviceResponse).toList();
        return new PageResponse<>(items,result.getNumber(),result.getSize(),result.getTotalPages(),result.isFirst(),result.isLast(),result.hasNext(),result.hasPrevious());


    }

    @Transactional(readOnly = true)
    public List<DeviceSimulationTargetResponse>
    getSimulationTargets() {

        return deviceRepository
                .findAllByStatus(DeviceStatus.ACTIVE)
                .stream()
                .map(device ->
                        new DeviceSimulationTargetResponse(
                                device.getDeviceUid(),
                                device.getUserId(),
                                device.getType().name(),
                                device.getModel() == null
                                        ? device.getCustomRatedPowerW()
                                        : device.getModel()
                                        .getRatedPowerW()
                        )
                )
                .toList();
    }


    public void  deleteDevice(Long id){
        deviceRepository.findById(id).orElseThrow(DeviceNotExist::new);
        deviceRepository.deleteById(id);
    }
    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }

        return Math.min(size, MAX_SIZE);
    }
}
