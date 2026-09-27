package com.homeenergytracker.deviceservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="device_models")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DeviceModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "manufacturer",
            nullable = false,
            length = 100
    )
    private String manufacturer;

    @Column(
            name = "model_name",
            nullable = false,
            length = 150
    )
    private String modelName;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "type",
            nullable = false,
            length = 50
    )
    private DeviceType type;

    @Column(
            name = "rated_power_w",
            precision = 10,
            scale = 3
    )
    private BigDecimal ratedPowerW;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private DeviceModelStatus status;

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

        if (status == null) {
            status = DeviceModelStatus.ACTIVE;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }



}
