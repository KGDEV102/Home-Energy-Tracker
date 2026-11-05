package com.homeenergytracker.userservice.entity;

public enum RefreshTokenRevocationReason {
    ROTATED,
    LOGOUT,
    REUSE_DETECTED,
    USER_DISABLED,
    ADMIN_REVOKED
}
