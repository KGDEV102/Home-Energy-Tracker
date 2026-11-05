package com.homeenergytracker.notificationservice.mail;

import com.homeenergytracker.notificationservice.dto.UserNotificationContactResponse;
import com.homeenergytracker.notificationservice.entity.DeliveryStatus;
import com.homeenergytracker.notificationservice.entity.Notification;
import com.homeenergytracker.notificationservice.exception.UserServiceUnavailableException;
import com.homeenergytracker.notificationservice.repository.NotificationRepository;
import com.homeenergytracker.notificationservice.service.UserContactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.Objects;

@Service
public class SmtpMailService implements MailService {
    private static final Logger log =
            LoggerFactory.getLogger(SmtpMailService.class);

    private final SmtpMailGateway smtpMailGateway;
    private final NotificationRepository notificationRepository;
    private final UserContactService userContactService;
    private final String fromAddress;
    private final int maxAttempts;

    public SmtpMailService(
            SmtpMailGateway smtpMailGateway,
            NotificationRepository notificationRepository,
            UserContactService userContactService,
            @Value("${notification.mail.from}") String fromAddress,
            @Value("${notification.delivery.max-attempts}") int maxAttempts
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "notification.delivery.max-attempts must be at least 1"
            );
        }
        this.smtpMailGateway = smtpMailGateway;
        this.notificationRepository = notificationRepository;
        this.userContactService = userContactService;
        this.fromAddress = fromAddress;
        this.maxAttempts = maxAttempts;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNotification(Long notificationId, String claimOwner) {
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Notification not found: " + notificationId
                ));

        if (notification.getDeliveryStatus() != DeliveryStatus.PROCESSING
                || !Objects.equals(notification.getClaimedBy(), claimOwner)) {
            log.debug(
                    "Skipping notification because the claim is no longer owned: notificationId={}, claimOwner={}",
                    notificationId,
                    claimOwner
            );
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

            smtpMailGateway.send(message);

            notification.setDeliveryStatus(DeliveryStatus.SENT);
            notification.setSentAt(Instant.now());
            notification.setNextAttemptAt(null);
            notification.setLastDeliveryError(null);
            clearClaim(notification);
        } catch (RuntimeException exception) {
            updateFailureState(notification, exception);
            notification.setLastDeliveryError(errorMessage(exception));
            clearClaim(notification);

            log.error(
                    "Could not send notification email: notificationId={}, userId={}",
                    notification.getId(),
                    notification.getUserId(),
                    exception
            );
        }
    }

    private void clearClaim(Notification notification) {
        notification.setClaimedAt(null);
        notification.setLockedUntil(null);
        notification.setClaimedBy(null);
    }

    private void updateFailureState(
            Notification notification,
            RuntimeException exception
    ) {
        if (!isRetryable(exception)
                || notification.getDeliveryAttempts() >= maxAttempts) {
            notification.setDeliveryStatus(DeliveryStatus.FAILED);
            notification.setNextAttemptAt(null);
            return;
        }

        notification.setDeliveryStatus(DeliveryStatus.RETRYING);
        notification.setNextAttemptAt(
                Instant.now().plusSeconds(
                        backoffSeconds(notification.getDeliveryAttempts())
                )
        );
    }

    private boolean isRetryable(RuntimeException exception) {
        if (exception instanceof HttpClientErrorException clientError) {
            return clientError.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
        }

        return exception instanceof HttpServerErrorException
                || exception instanceof ResourceAccessException
                || exception instanceof UserServiceUnavailableException
                || exception instanceof MailException;
    }

    private long backoffSeconds(int attempts) {
        return switch (attempts) {
            case 1 -> 30;
            case 2 -> 120;
            case 3 -> 600;
            default -> 1800;
        };
    }

    private String errorMessage(RuntimeException exception) {
        if (exception.getMessage() == null
                || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return exception.getMessage();
    }
}
