package com.homeenergytracker.deviceservice.mapper;

import com.homeenergytracker.deviceservice.dto.DeviceResponse;
import com.homeenergytracker.deviceservice.dto.UpdateDevice;
import com.homeenergytracker.deviceservice.entity.Device;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface DeviceMapper {
    DeviceResponse toDeviceResponse(Device device);
    @Mapping(target="id",ignore=true)
    @Mapping(target="deviceUid", ignore=true)
    @Mapping(target = "userId",ignore=true)
    @BeanMapping(
            ignoreByDefault = true,
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "name", source = "name")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "status", source = "status")
   void updateEntity(
           UpdateDevice request,
           @MappingTarget Device device
    );
}
