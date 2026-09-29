package com.homeenergytracker.notificationservice.service;

import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.common.event.ReadingSource;
import com.homeenergytracker.notificationservice.entity.DeliveryStatus;
import com.homeenergytracker.notificationservice.entity.Notification;
import com.homeenergytracker.notificationservice.entity.NotificationRule;
import com.homeenergytracker.notificationservice.entity.NotificationType;
import com.homeenergytracker.notificationservice.event.NotificationCreatedEvent;
import com.homeenergytracker.notificationservice.repository.NotificationRepository;
import com.homeenergytracker.notificationservice.repository.NotificationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationEvaluationServiceTest  {
    private static final UUID EVENT_ID =
            UUID.fromString("83179ef8-90b8-4fbf-99c6-83ace668990f");

    private static final UUID DEVICE_UID =
            UUID.fromString("4d7e16e7-808c-4ba0-92c4-91fc1a470fd4");

    private static final Instant MEASURED_AT =
            Instant.parse("2026-09-28T03:00:00Z");

    @Mock
    private NotificationRuleRepository ruleRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotificationEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationEvaluationService(
                ruleRepository,
                notificationRepository,
                eventPublisher
        );
    }
    private NotificationRule powerThresholdRule(String threshold) {
        NotificationRule rule = new NotificationRule();

        rule.setId(10L);
        rule.setUserId(1L);
        rule.setDeviceUid(DEVICE_UID);
        rule.setType(NotificationType.POWER_THRESHOLD);
        rule.setThreshold(new BigDecimal(threshold));
        rule.setCooldownSeconds(300);
        rule.setEnabled(true);

        return rule;
    }

    private EnergyUsageEvent eventWithPower(String averagePowerW) {
        return new EnergyUsageEvent(
                EVENT_ID,
                1,
                DEVICE_UID,
                1L,
                "AIR_CONDITIONER",
                new BigDecimal(averagePowerW),
                new BigDecimal("1.670"),
                MEASURED_AT,
                MEASURED_AT.plusSeconds(1),
                ReadingSource.SIMULATOR
        );
    }
    @Test
    void shouldCreateNotificationWhenPowerExceedsThreshold() {
        // Arrange
        EnergyUsageEvent event = eventWithPower("1200.000");
        NotificationRule rule = powerThresholdRule("1000.000");

        when(ruleRepository
                .findAllByUserIdAndDeviceUidAndEnabledTrue(
                        1L,
                        DEVICE_UID
                ))
                .thenReturn(List.of(rule));

        when(notificationRepository.existsByEventIdAndRuleId(
                EVENT_ID,
                10L
        )).thenReturn(false);

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    notification.setId(100L);
                    return notification;
                });

        // Act
        service.evaluate(event);

        // Assert dữ liệu được lưu
        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository)
                .save(notificationCaptor.capture());

        Notification savedNotification =
                notificationCaptor.getValue();

        assertEquals(EVENT_ID, savedNotification.getEventId());
        assertEquals(10L, savedNotification.getRuleId());
        assertEquals(1L, savedNotification.getUserId());
        assertEquals(DEVICE_UID, savedNotification.getDeviceUid());

        assertEquals(
                NotificationType.POWER_THRESHOLD,
                savedNotification.getType()
        );

        assertEquals(
                new BigDecimal("1200.000"),
                savedNotification.getTriggerValue()
        );

        assertEquals(
                new BigDecimal("1000.000"),
                savedNotification.getThresholdValue()
        );

        assertEquals(
                DeliveryStatus.PENDING,
                savedNotification.getDeliveryStatus()
        );

        assertEquals(MEASURED_AT, savedNotification.getMeasuredAt());

        // Cooldown phải được bắt đầu
        assertNotNull(rule.getLastTriggeredAt());

        // Event gửi mail phải được publish
        ArgumentCaptor<NotificationCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationCreatedEvent.class);

        verify(eventPublisher)
                .publishEvent(eventCaptor.capture());

        assertEquals(
                100L,
                eventCaptor.getValue().notificationId()
        );
    }
    @Test
    void shouldNotCreateNotificationWhenPowerEqualsThreshold() {
        // Arrange
        EnergyUsageEvent event = eventWithPower("1000.000");
        NotificationRule rule = powerThresholdRule("1000.000");

        when(ruleRepository
                .findAllByUserIdAndDeviceUidAndEnabledTrue(
                        1L,
                        DEVICE_UID
                ))
                .thenReturn(List.of(rule));

        // Act
        service.evaluate(event);

        // Assert
        verify(notificationRepository, never())
                .save(any(Notification.class));

        verify(eventPublisher, never())
                .publishEvent(any(NotificationCreatedEvent.class));

        assertNull(rule.getLastTriggeredAt());
    }

    @Test
    void shouldNotCreateDuplicateNotification() {
        // Arrange
        EnergyUsageEvent event = eventWithPower("1200.000");
        NotificationRule rule = powerThresholdRule("1000.000");

        when(ruleRepository
                .findAllByUserIdAndDeviceUidAndEnabledTrue(
                        1L,
                        DEVICE_UID
                ))
                .thenReturn(List.of(rule));

        when(notificationRepository.existsByEventIdAndRuleId(
                EVENT_ID,
                10L
        )).thenReturn(true);

        // Act
        service.evaluate(event);

        // Assert
        verify(notificationRepository, never())
                .save(any(Notification.class));

        verify(eventPublisher, never())
                .publishEvent(any(NotificationCreatedEvent.class));

        assertNull(rule.getLastTriggeredAt());
    }

    @Test
    void shouldNotCreateNotificationDuringCooldown() {
        // Arrange
        EnergyUsageEvent event = eventWithPower("1200.000");
        NotificationRule rule = powerThresholdRule("1000.000");

        rule.setCooldownSeconds(300);
        rule.setLastTriggeredAt(Instant.now());

        when(ruleRepository
                .findAllByUserIdAndDeviceUidAndEnabledTrue(
                        1L,
                        DEVICE_UID
                ))
                .thenReturn(List.of(rule));

        // Act
        service.evaluate(event);

        // Assert
        verify(notificationRepository, never())
                .save(any(Notification.class));

        verify(eventPublisher, never())
                .publishEvent(any(NotificationCreatedEvent.class));
    }

    @Test
    void shouldCreateNotificationWhenCooldownExpired() {
        EnergyUsageEvent event = eventWithPower("1200.000");
        NotificationRule rule = powerThresholdRule("1000.000");
        Instant previousTriggeredAt = Instant.now().minusSeconds(301);

        rule.setCooldownSeconds(300);
        rule.setLastTriggeredAt(previousTriggeredAt);

        when(ruleRepository
                .findAllByUserIdAndDeviceUidAndEnabledTrue(
                        1L,
                        DEVICE_UID
                ))
                .thenReturn(List.of(rule));

        when(notificationRepository.existsByEventIdAndRuleId(
                EVENT_ID,
                10L
        )).thenReturn(false);

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    notification.setId(101L);
                    return notification;
                });

        service.evaluate(event);

        verify(notificationRepository).save(any(Notification.class));
        verify(eventPublisher).publishEvent(any(NotificationCreatedEvent.class));

        assertTrue(rule.getLastTriggeredAt().isAfter(previousTriggeredAt));
    }

    @Test
    void shouldIgnoreRuleThatIsNotPowerThreshold() {
        EnergyUsageEvent event = eventWithPower("1200.000");
        NotificationRule rule = powerThresholdRule("1000.000");
        rule.setType(NotificationType.DEVICE_OFFLINE);

        when(ruleRepository
                .findAllByUserIdAndDeviceUidAndEnabledTrue(
                        1L,
                        DEVICE_UID
                ))
                .thenReturn(List.of(rule));

        service.evaluate(event);

        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(eventPublisher);
        assertNull(rule.getLastTriggeredAt());
    }

    @Test
    void shouldRejectEventWithoutEventId() {
        EnergyUsageEvent event = new EnergyUsageEvent(
                null,
                1,
                DEVICE_UID,
                1L,
                "AIR_CONDITIONER",
                new BigDecimal("1200.000"),
                new BigDecimal("1.670"),
                MEASURED_AT,
                MEASURED_AT.plusSeconds(1),
                ReadingSource.SIMULATOR
        );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.evaluate(event)
        );

        assertEquals("eventId is required", exception.getMessage());
        verifyNoInteractions(ruleRepository);
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(eventPublisher);
    }



}
