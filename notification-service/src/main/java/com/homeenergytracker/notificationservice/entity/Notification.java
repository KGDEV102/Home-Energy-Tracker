package com.homeenergytracker.notificationservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notifications_event_rule",
                columnNames = {"event_id", "rule_id"}
        )
)
@Getter
@Setter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "event_id",
            nullable = false,
            updatable = false
    )
    private UUID eventId;
    @Column(
            name = "rule_id",
            nullable = false,
            updatable = false
    )
    private Long ruleId;
    @Column(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private Long userId;
    @Column(
            name = "device_uid",
            nullable = false,
            updatable = false
    )
    private UUID deviceUid;
    @Enumerated(EnumType.STRING)
    @Column(
            name = "type",
            nullable = false,
            length = 50,
            updatable = false
    )
    private NotificationType type;
    @Column(
            name = "title",
            nullable = false,
            length = 255,
            updatable = false
    )
    private String title;

    @Column(
            name = "message",
            nullable = false,
            columnDefinition = "TEXT",
            updatable = false
    )
    private String message;

    @Column(
            name = "trigger_value",
            nullable = false,
            precision = 12,
            scale = 3,
            updatable = false
    )
    private BigDecimal triggerValue;

    @Column(
            name = "threshold_value",
            nullable = false,
            precision = 12,
            scale = 3,
            updatable = false
    )
    private BigDecimal thresholdValue;

    @Column(
            name = "measured_at",
            nullable = false,
            updatable = false
    )
    private Instant measuredAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }


}
