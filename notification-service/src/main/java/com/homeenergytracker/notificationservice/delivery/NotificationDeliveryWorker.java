package com.homeenergytracker.notificationservice.delivery;

import com.homeenergytracker.notificationservice.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationDeliveryWorker {
    private static final Logger log =
            LoggerFactory.getLogger(NotificationDeliveryWorker.class);

    private final NotificationClaimService notificationClaimService;
    private final MailService mailService;
    private final int maxAttempts;
    private final int batchSize;
    private final String workerId;

    public NotificationDeliveryWorker(
            NotificationClaimService notificationClaimService,
            MailService mailService,
            @Value("${notification.delivery.max-attempts}") int maxAttempts,
            @Value("${notification.delivery.batch-size}") int batchSize,
            @Value("${notification.delivery.worker-id}") String workerId
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "notification.delivery.max-attempts must be at least 1"
            );
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "notification.delivery.batch-size must be at least 1"
            );
        }
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "notification.delivery.worker-id must not be blank"
            );
        }
        this.notificationClaimService = notificationClaimService;
        this.mailService = mailService;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.workerId = workerId;
    }

    @Scheduled(fixedDelayString = "${notification.delivery.poll-delay-ms}")
    public void deliverDueNotifications() {
        List<Long> notificationIds =
                notificationClaimService.claimDueNotifications(
                        workerId,
                        maxAttempts,
                        batchSize
                );

        notificationIds.forEach(this::deliverNotification);
    }

    private void deliverNotification(Long notificationId) {
        try {
            mailService.sendNotification(notificationId, workerId);
        } catch (RuntimeException exception) {
            log.error(
                    "Notification delivery worker could not process notificationId={}",
                    notificationId,
                    exception
            );
        }
    }
}
