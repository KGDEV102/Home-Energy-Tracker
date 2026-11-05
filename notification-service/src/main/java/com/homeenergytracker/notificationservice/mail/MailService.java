package com.homeenergytracker.notificationservice.mail;

public interface MailService {
    void sendNotification(Long notificationId, String claimOwner);
}
