package com.homeenergytracker.notificationservice.service;

import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.notificationservice.entity.Notification;
import com.homeenergytracker.notificationservice.entity.NotificationRule;
import com.homeenergytracker.notificationservice.entity.NotificationType;
import com.homeenergytracker.notificationservice.event.NotificationCreatedEvent;
import com.homeenergytracker.notificationservice.repository.NotificationRepository;
import com.homeenergytracker.notificationservice.repository.NotificationRuleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class NotificationEvaluationService {
    private static final String POWER_THRESHOLD_TITLE =
            "Power usage threshold exceeded";

    private final NotificationRuleRepository notificationRuleRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationEvaluationService(
            NotificationRuleRepository notificationRuleRepository,
            NotificationRepository notificationRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.notificationRuleRepository = notificationRuleRepository;
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void evaluate(EnergyUsageEvent event) {
        validateRequiredFields(event);

        List<NotificationRule> rules =
                notificationRuleRepository
                        .findAllByUserIdAndDeviceUidAndEnabledTrue(
                                event.userId(),
                                event.deviceUid()
                        );

        Instant now = Instant.now();

        rules.stream()
                .filter(this::isPowerThresholdRule)
                .filter(rule -> exceedsThreshold(event, rule))
                .filter(rule -> isCooldownExpired(rule, now))
                .forEach(rule ->
                        createNotificationIfAbsent(event, rule, now)
                );
    }

    private boolean isPowerThresholdRule(NotificationRule rule) {
        return rule.getType() == NotificationType.POWER_THRESHOLD;
    }

    private boolean exceedsThreshold(
            EnergyUsageEvent event,
            NotificationRule rule
    ) {
        return event.averagePowerW().compareTo(rule.getThreshold()) > 0;
    }

    private boolean isCooldownExpired(
            NotificationRule rule,
            Instant now
    ) {
        if (rule.getLastTriggeredAt() == null) {
            return true;
        }

        Instant nextAllowedAt = rule.getLastTriggeredAt()
                .plusSeconds(rule.getCooldownSeconds());

        return !now.isBefore(nextAllowedAt);
    }

    private void createNotificationIfAbsent(
            EnergyUsageEvent event,
            NotificationRule rule,
            Instant now
    ) {
        if (notificationRepository.existsByEventIdAndRuleId(
                event.eventId(),
                rule.getId()
        )) {
            return;
        }

        Notification notification = new Notification();
        notification.setEventId(event.eventId());
        notification.setRuleId(rule.getId());
        notification.setUserId(event.userId());
        notification.setDeviceUid(event.deviceUid());
        notification.setType(rule.getType());
        notification.setTitle(POWER_THRESHOLD_TITLE);
        notification.setMessage(buildPowerThresholdMessage(
                event.averagePowerW(),
                rule.getThreshold()
        ));
        notification.setTriggerValue(event.averagePowerW());
        notification.setThresholdValue(rule.getThreshold());
        notification.setMeasuredAt(event.measuredAt());

        Notification savedNotification =
                notificationRepository.save(notification);
        rule.setLastTriggeredAt(now);

        eventPublisher.publishEvent(
                new NotificationCreatedEvent(savedNotification.getId())
        );
    }

    private String buildPowerThresholdMessage(
            BigDecimal averagePowerW,
            BigDecimal threshold
    ) {
        return "Average power "
                + averagePowerW.toPlainString()
                + " W exceeds threshold "
                + threshold.toPlainString()
                + " W";
    }

    private void validateRequiredFields(EnergyUsageEvent event) {
        Objects.requireNonNull(event, "event is required");
        Objects.requireNonNull(event.eventId(), "eventId is required");
        Objects.requireNonNull(event.userId(), "userId is required");
        Objects.requireNonNull(event.deviceUid(), "deviceUid is required");
        Objects.requireNonNull(
                event.averagePowerW(),
                "averagePowerW is required"
        );
        Objects.requireNonNull(event.measuredAt(), "measuredAt is required");
    }
}
