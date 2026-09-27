package com.homeenergytracker.deviceservice.mapper;

import com.homeenergytracker.deviceservice.dto.DeviceModelDto;
import com.homeenergytracker.deviceservice.entity.DeviceModel;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DeviceModelMapper {
    DeviceModel toEntity(DeviceModelDto deviceModelDto);
    DeviceModelDto toDto(DeviceModel deviceModel);
}
