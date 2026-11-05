package com.homeenergytracker.userservice.entity;

import ch.qos.logback.core.status.Status;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name="users")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="first_name",nullable = false)
   private String firstName;
    @Column(name="last_name",nullable = false)
   private String lastName;
    @Column(name = "email",nullable = false,unique = true)
    private String email;
    // Nullable for existing profiles that have not set a password yet.
    @Column(name = "password_hash", length = 255)
    private String passwordHash;
    @Column(nullable = true,columnDefinition = "TEXT")
    private String address;



    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(nullable = false,name="created_at")
    private Instant createdAt;
    @PrePersist
    public void prePersist() {
        this.status = UserStatus.ACTIVE;
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
