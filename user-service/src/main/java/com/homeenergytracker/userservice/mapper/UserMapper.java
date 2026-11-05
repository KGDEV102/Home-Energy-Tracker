package com.homeenergytracker.userservice.mapper;

import com.homeenergytracker.userservice.dto.UpdateUser;
import com.homeenergytracker.userservice.dto.UserDto;
import com.homeenergytracker.userservice.entity.User;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring"
)
public interface UserMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "createdAt",ignore = true)
    @Mapping(target = "updatedAt",ignore = true)
    User toEntity(UserDto userDto);
    UserDto toDto(User user);
    // BỔ SUNG: chỉ cập nhật các field khác null.
    @BeanMapping(
            nullValuePropertyMappingStrategy =
                    NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "passwordHash", ignore = true)
    void updateEntity(
            UpdateUser request,
            @MappingTarget User user
    );
}
