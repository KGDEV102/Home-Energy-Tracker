package com.homeenergytracker.deviceservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="devices")
@Getter
@Setter
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "device_uid",
            nullable = false,
            length = 100
    )
    private UUID deviceUid;

    /*
     * Chỉ có giá trị khi model == null.
     */
    @Column(
            name = "custom_manufacturer",
            length = 100,
            nullable = true
    )
    private String customManufacturer;

    /*
     * Bắt buộc khi model == null.
     */
    @Column(
            name = "custom_model_name",
            length = 150,
            nullable = true
    )
    private String customModelName;

    /*
     * Rated power supplied by the user for a custom device.
     * Catalog devices obtain rated power from DeviceModel instead.
     */
    @Column(
            name = "custom_rated_power_w",
            precision = 10,
            scale = 3
    )
    private BigDecimal customRatedPowerW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "model_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_devices_model")
    )
    private DeviceModel model;


    @Column(name="user_id",nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    private DeviceType type;

    private String location;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceStatus status;

    @Column(nullable = false,name="created_at",updatable = false)
    private Instant createdAt;
    @PrePersist
    public void prePersist() {
        if(this.status == null) {
            this.status = DeviceStatus.ACTIVE;
        }
        if (deviceUid == null) {
            deviceUid = UUID.randomUUID();
        }
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    @Column(nullable = false,name="updated_at")
    private Instant updatedAt;
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
