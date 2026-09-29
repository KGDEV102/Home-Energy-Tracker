package com.homeenergytracker.notificationservice.mapper;

import com.homeenergytracker.notificationservice.dto.CreateNotificationRule;
import com.homeenergytracker.notificationservice.dto.UpdateNotificationRule;
import com.homeenergytracker.notificationservice.entity.NotificationRule;
import com.homeenergytracker.notificationservice.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRuleMapperTest {

    private static final UUID DEVICE_UID =
            UUID.fromString("4d7e16e7-808c-4ba0-92c4-91fc1a470fd4");

    private final NotificationRuleMapper mapper =
            Mappers.getMapper(NotificationRuleMapper.class);

    @Test
    void shouldMapCreateRequestToEntity() {
        CreateNotificationRule request = new CreateNotificationRule(
                DEVICE_UID,
                NotificationType.POWER_THRESHOLD,
                new BigDecimal("1000.000"),
                300L,
                true
        );

        NotificationRule result = mapper.toEntity(request);

        assertEquals(DEVICE_UID, result.getDeviceUid());
        assertEquals(NotificationType.POWER_THRESHOLD, result.getType());
        assertEquals(new BigDecimal("1000.000"), result.getThreshold());
        assertEquals(300L, result.getCooldownSeconds());
        assertTrue(result.isEnabled());
        assertNull(result.getUserId());
    }

    @Test
    void shouldKeepEnabledDefaultWhenCreateRequestOmitsIt() {
        CreateNotificationRule request = new CreateNotificationRule(
                DEVICE_UID,
                NotificationType.POWER_THRESHOLD,
                new BigDecimal("1000.000"),
                300L,
                null
        );

        NotificationRule result = mapper.toEntity(request);

        assertTrue(result.isEnabled());
    }

    @Test
    void shouldIgnoreNullFieldsWhenUpdatingEntity() {
        NotificationRule entity = existingRule();
        UpdateNotificationRule request = new UpdateNotificationRule(
                null,
                new BigDecimal("1500.000"),
                null,
                false
        );

        mapper.updateEntity(request, entity);

        assertEquals(NotificationType.POWER_THRESHOLD, entity.getType());
        assertEquals(new BigDecimal("1500.000"), entity.getThreshold());
        assertEquals(300L, entity.getCooldownSeconds());
        assertFalse(entity.isEnabled());
    }

    private NotificationRule existingRule() {
        NotificationRule rule = new NotificationRule();
        rule.setUserId(1L);
        rule.setDeviceUid(DEVICE_UID);
        rule.setType(NotificationType.POWER_THRESHOLD);
        rule.setThreshold(new BigDecimal("1000.000"));
        rule.setCooldownSeconds(300L);
        rule.setEnabled(true);
        return rule;
    }
}
