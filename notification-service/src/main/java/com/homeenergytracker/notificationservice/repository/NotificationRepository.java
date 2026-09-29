package com.homeenergytracker.notificationservice.repository;

import com.homeenergytracker.notificationservice.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByEventIdAndRuleId(UUID eventId, Long ruleId);
}
