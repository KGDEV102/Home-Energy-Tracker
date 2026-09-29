package com.homeenergytracker.notificationservice.event;

import com.homeenergytracker.notificationservice.mail.MailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationCreatedEventListener {
    private final MailService mailService;

    public NotificationCreatedEventListener(MailService mailService) {
        this.mailService = mailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationCreatedEvent event) {
        mailService.sendNotification(event.notificationId());
    }
}
