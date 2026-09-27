package com.homeenergytracker.notificationservice.mapper;

import com.homeenergytracker.notificationservice.dto.CreateNotificationRule;
import com.homeenergytracker.notificationservice.dto.UpdateNotificationRule;
import com.homeenergytracker.notificationservice.entity.NotificationRule;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface NotificationRuleMapper {
    NotificationRule toEntity(CreateNotificationRule notificationRule);

    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    void updateEntity(
            UpdateNotificationRule updateNotificationRule,
            @MappingTarget NotificationRule entity
    );
}
