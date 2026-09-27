package com.homeenergytracker.deviceservice.service;

import com.homeenergytracker.deviceservice.dto.DeviceModelDto;
import com.homeenergytracker.deviceservice.dto.SliceResponse;
import com.homeenergytracker.deviceservice.entity.DeviceModel;
import com.homeenergytracker.deviceservice.entity.DeviceType;
import com.homeenergytracker.deviceservice.exception.DeviceModelNotExist;
import com.homeenergytracker.deviceservice.mapper.DeviceModelMapper;
import com.homeenergytracker.deviceservice.repository.DeviceModelRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeviceModelService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 20;
    private final DeviceModelRepository deviceModelRepository;
    private final DeviceModelMapper deviceModelMapper;
    public DeviceModelService(DeviceModelRepository deviceModelRepository, DeviceModelMapper deviceModelMapper) {
        this.deviceModelRepository = deviceModelRepository;
        this.deviceModelMapper = deviceModelMapper;
    }
    public SliceResponse<DeviceModelDto> searchModels(
            String keyword,
            DeviceType type,
            int page,
            int size
    )
    {
        String normalizedKeyword = normalizeKeyword(keyword);
        String typeValue = type == null
                ? null
                : type.name();

        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize
        );

        Slice<DeviceModel> result =
                deviceModelRepository.searchActiveModels(
                        normalizedKeyword,
                        typeValue,
                        pageable
                );

        List<DeviceModelDto> items =
                result.getContent()
                        .stream()
                        .map(deviceModelMapper::toDto)
                        .toList();
        return new SliceResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.hasNext()
        );
    }
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
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
    public DeviceModelDto getDeviceModelById(Long id)
    {
        DeviceModel deviceModel = deviceModelRepository.findById(id).orElseThrow(DeviceModelNotExist::new);
        return deviceModelMapper.toDto(deviceModel);
    }
}
