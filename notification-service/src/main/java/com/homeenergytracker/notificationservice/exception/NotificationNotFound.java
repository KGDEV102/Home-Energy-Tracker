package com.homeenergytracker.notificationservice.exception;

import com.homeenergytracker.common.exception.BusinessException;

public class NotificationNotFound extends BusinessException {
    public NotificationNotFound() {
        super("NOTIFICATION_NOT_FOUND", "Notification not found");
    }
}
