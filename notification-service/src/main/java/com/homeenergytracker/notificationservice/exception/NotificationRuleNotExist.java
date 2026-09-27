package com.homeenergytracker.notificationservice.exception;

public class NotificationRuleNotExist extends com.homeenergytracker.common.exception.BusinessException {
    public NotificationRuleNotExist() {
        super("NOTIFICATION_RULE_NOT_EXIST","Notification Rule Not Exist");
    }
}
