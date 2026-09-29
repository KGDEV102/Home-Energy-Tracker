package com.homeenergytracker.notificationservice.mail;

import com.homeenergytracker.notificationservice.dto.UserNotificationContactResponse;
import com.homeenergytracker.notificationservice.entity.DeliveryStatus;
import com.homeenergytracker.notificationservice.entity.Notification;
import com.homeenergytracker.notificationservice.repository.NotificationRepository;
import com.homeenergytracker.notificationservice.service.UserContactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SmtpMailService implements MailService {
    private static final Logger log =
            LoggerFactory.getLogger(SmtpMailService.class);

    private final JavaMailSender mailSender;
    private final NotificationRepository notificationRepository;
    private final UserContactService userContactService;
    private final String fromAddress;

    public SmtpMailService(
            JavaMailSender mailSender,
            NotificationRepository notificationRepository,
            UserContactService userContactService,
            @Value("${notification.mail.from}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.notificationRepository = notificationRepository;
        this.userContactService = userContactService;
        this.fromAddress = fromAddress;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNotification(Long notificationId) {
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Notification not found: " + notificationId
                ));

        if (notification.getDeliveryStatus() == DeliveryStatus.SENT) {
            return;
        }

        notification.setDeliveryAttempts(
                notification.getDeliveryAttempts() + 1
        );

        try {
            UserNotificationContactResponse contact =
                    userContactService.getNotificationContact(
                            notification.getUserId()
                    );

            notification.setRecipientEmail(contact.email());

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(contact.email());
            message.setSubject(notification.getTitle());
            message.setText(notification.getMessage());

            mailSender.send(message);

            notification.setDeliveryStatus(DeliveryStatus.SENT);
            notification.setSentAt(Instant.now());
            notification.setLastDeliveryError(null);
        } catch (RuntimeException exception) {
            notification.setDeliveryStatus(DeliveryStatus.FAILED);
            notification.setLastDeliveryError(errorMessage(exception));

            log.error(
                    "Could not send notification email: notificationId={}, userId={}",
                    notification.getId(),
                    notification.getUserId(),
                    exception
            );
        }
    }

    private String errorMessage(RuntimeException exception) {
        if (exception.getMessage() == null
                || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return exception.getMessage();
    }
}
