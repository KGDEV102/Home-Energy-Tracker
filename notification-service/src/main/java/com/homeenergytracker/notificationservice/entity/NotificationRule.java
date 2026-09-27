package com.homeenergytracker.notificationservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "notification_rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notification_rules_user_device_type",
                columnNames = {"user_id", "device_uid", "type"}
        )
)
@Getter
@Setter
public class NotificationRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(
            name="user_id",
            nullable = false
    )
    private Long userId;
    @Column(
            name="device_uid",
            nullable = false
    )
    private UUID deviceUid;
    @Enumerated(EnumType.STRING)
    @Column(
            name = "type",
            nullable = false,
            length = 50
    )
    private NotificationType type;
    @Column(
            name = "threshold",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal threshold;
    @Column(
            name = "cooldown_seconds",
            nullable = false
    )
    private long cooldownSeconds;
    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;
    @Column(
            name = "enabled",
            nullable = false
    )
    private boolean enabled = true;
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
