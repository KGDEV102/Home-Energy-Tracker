package com.homeenergytracker.notificationservice.mail;

import com.homeenergytracker.notificationservice.dto.UserNotificationContactResponse;
import com.homeenergytracker.notificationservice.entity.DeliveryStatus;
import com.homeenergytracker.notificationservice.entity.Notification;
import com.homeenergytracker.notificationservice.entity.NotificationType;
import com.homeenergytracker.notificationservice.exception.UserServiceUnavailableException;
import com.homeenergytracker.notificationservice.repository.NotificationRepository;
import com.homeenergytracker.notificationservice.service.UserContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpMailServiceTest {
    private static final Long NOTIFICATION_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final String FROM_ADDRESS =
            "no-reply@homeenergytracker.local";
    private static final String RECIPIENT_EMAIL =
            "user@example.com";
    private static final String CLAIM_OWNER = "worker-a";

    @Mock
    private SmtpMailGateway smtpMailGateway;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserContactService userContactService;

    private SmtpMailService service;

    @BeforeEach
    void setUp() {
        service = new SmtpMailService(
                smtpMailGateway,
                notificationRepository,
                userContactService,
                FROM_ADDRESS,
                5
        );
    }

    @Test
    void shouldSendEmailAndMarkNotificationAsSent() {
        Notification notification = pendingNotification();

        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));
        when(userContactService.getNotificationContact(USER_ID))
                .thenReturn(validContact());

        service.sendNotification(NOTIFICATION_ID, CLAIM_OWNER);

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(smtpMailGateway).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals(FROM_ADDRESS, message.getFrom());
        assertArrayEquals(
                new String[]{RECIPIENT_EMAIL},
                message.getTo()
        );
        assertEquals(notification.getTitle(), message.getSubject());
        assertEquals(notification.getMessage(), message.getText());

        assertEquals(DeliveryStatus.SENT, notification.getDeliveryStatus());
        assertEquals(1, notification.getDeliveryAttempts());
        assertEquals(RECIPIENT_EMAIL, notification.getRecipientEmail());
        assertNotNull(notification.getSentAt());
        assertNull(notification.getLastDeliveryError());
        assertClaimCleared(notification);
    }

    @Test
    void shouldNotSendEmailWhenNotificationWasAlreadySent() {
        Notification notification = pendingNotification();
        notification.setDeliveryStatus(DeliveryStatus.SENT);
        notification.setDeliveryAttempts(1);

        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));

        service.sendNotification(NOTIFICATION_ID, CLAIM_OWNER);

        verifyNoInteractions(userContactService);
        verifyNoInteractions(smtpMailGateway);
        assertEquals(1, notification.getDeliveryAttempts());
    }

    @Test
    void shouldThrowWhenNotificationDoesNotExist() {
        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.sendNotification(NOTIFICATION_ID, CLAIM_OWNER)
        );

        assertEquals(
                "Notification not found: 100",
                exception.getMessage()
        );
        verifyNoInteractions(userContactService);
        verifyNoInteractions(smtpMailGateway);
    }

    @Test
    void shouldMarkNotificationAsFailedWhenContactCannotBeLoaded() {
        Notification notification = pendingNotification();

        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));
        when(userContactService.getNotificationContact(USER_ID))
                .thenThrow(new IllegalStateException(
                        "Could not load notification contact"
                ));

        service.sendNotification(NOTIFICATION_ID, CLAIM_OWNER);

        verify(smtpMailGateway, never()).send(any(SimpleMailMessage.class));
        assertEquals(DeliveryStatus.FAILED, notification.getDeliveryStatus());
        assertEquals(1, notification.getDeliveryAttempts());
        assertNull(notification.getRecipientEmail());
        assertNull(notification.getSentAt());
        assertEquals(
                "Could not load notification contact",
                notification.getLastDeliveryError()
        );
        assertClaimCleared(notification);
    }

    @Test
    void shouldScheduleRetryWhenSmtpSendFails() {
        Notification notification = pendingNotification();

        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));
        when(userContactService.getNotificationContact(USER_ID))
                .thenReturn(validContact());
        doThrow(new MailSendException("SMTP unavailable"))
                .when(smtpMailGateway)
                .send(any(SimpleMailMessage.class));

        service.sendNotification(NOTIFICATION_ID, CLAIM_OWNER);

        verify(smtpMailGateway).send(any(SimpleMailMessage.class));
        assertEquals(DeliveryStatus.RETRYING, notification.getDeliveryStatus());
        assertEquals(1, notification.getDeliveryAttempts());
        assertEquals(RECIPIENT_EMAIL, notification.getRecipientEmail());
        assertNull(notification.getSentAt());
        assertNotNull(notification.getNextAttemptAt());
        assertEquals(
                "SMTP unavailable",
                notification.getLastDeliveryError()
        );
        assertClaimCleared(notification);
    }

    @Test
    void shouldScheduleRetryWhenUserServiceReturnsServerError() {
        Notification notification = pendingNotification();
        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));
        when(userContactService.getNotificationContact(USER_ID))
                .thenThrow(HttpServerErrorException.create(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "User service unavailable",
                        org.springframework.http.HttpHeaders.EMPTY,
                        new byte[0],
                        null
                ));

        service.sendNotification(NOTIFICATION_ID, CLAIM_OWNER);

        verify(smtpMailGateway, never()).send(any(SimpleMailMessage.class));
        assertEquals(DeliveryStatus.RETRYING, notification.getDeliveryStatus());
        assertNotNull(notification.getNextAttemptAt());
    }

    @Test
    void shouldScheduleRetryWithoutSendingWhenUserServiceCircuitIsOpen() {
        Notification notification = pendingNotification();
        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));
        when(userContactService.getNotificationContact(USER_ID))
                .thenThrow(new UserServiceUnavailableException(
                        "Could not load notification contact for user 1"
                ));

        service.sendNotification(NOTIFICATION_ID, CLAIM_OWNER);

        verify(smtpMailGateway, never()).send(any(SimpleMailMessage.class));
        assertEquals(DeliveryStatus.RETRYING, notification.getDeliveryStatus());
        assertEquals(1, notification.getDeliveryAttempts());
        assertNull(notification.getRecipientEmail());
        assertNull(notification.getSentAt());
        assertNotNull(notification.getNextAttemptAt());
        assertClaimCleared(notification);
    }

    @Test
    void shouldNotRetryWhenUserServiceRejectsInternalToken() {
        Notification notification = pendingNotification();
        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));
        when(userContactService.getNotificationContact(USER_ID))
                .thenThrow(HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "Internal token rejected",
                        org.springframework.http.HttpHeaders.EMPTY,
                        new byte[0],
                        null
                ));

        service.sendNotification(NOTIFICATION_ID, CLAIM_OWNER);

        assertEquals(DeliveryStatus.FAILED, notification.getDeliveryStatus());
        assertNull(notification.getNextAttemptAt());
        verify(smtpMailGateway, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldMarkAsFailedWhenRetryLimitIsReached() {
        Notification notification = pendingNotification();
        notification.setDeliveryAttempts(4);
        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));
        when(userContactService.getNotificationContact(USER_ID))
                .thenThrow(HttpServerErrorException.create(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "User service unavailable",
                        org.springframework.http.HttpHeaders.EMPTY,
                        new byte[0],
                        null
                ));

        service.sendNotification(NOTIFICATION_ID, CLAIM_OWNER);

        assertEquals(DeliveryStatus.FAILED, notification.getDeliveryStatus());
        assertNull(notification.getNextAttemptAt());
        assertEquals(5, notification.getDeliveryAttempts());
        assertClaimCleared(notification);
    }

    @Test
    void shouldNotSendWhenClaimBelongsToAnotherWorker() {
        Notification notification = pendingNotification();
        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));

        service.sendNotification(NOTIFICATION_ID, "worker-b");

        verifyNoInteractions(userContactService);
        verifyNoInteractions(smtpMailGateway);
        assertEquals(0, notification.getDeliveryAttempts());
        assertEquals(DeliveryStatus.PROCESSING, notification.getDeliveryStatus());
        assertEquals(CLAIM_OWNER, notification.getClaimedBy());
    }

    private UserNotificationContactResponse validContact() {
        return new UserNotificationContactResponse(
                USER_ID,
                RECIPIENT_EMAIL
        );
    }

    private Notification pendingNotification() {
        Notification notification = new Notification();
        notification.setId(NOTIFICATION_ID);
        notification.setEventId(UUID.randomUUID());
        notification.setRuleId(10L);
        notification.setUserId(USER_ID);
        notification.setDeviceUid(UUID.randomUUID());
        notification.setType(NotificationType.POWER_THRESHOLD);
        notification.setTitle("Power usage threshold exceeded");
        notification.setMessage("Average power exceeds threshold");
        notification.setTriggerValue(new BigDecimal("1200.000"));
        notification.setThresholdValue(new BigDecimal("1000.000"));
        notification.setMeasuredAt(Instant.parse("2026-09-28T03:00:00Z"));
        notification.setDeliveryStatus(DeliveryStatus.PROCESSING);
        notification.setDeliveryAttempts(0);
        notification.setClaimedAt(Instant.now());
        notification.setLockedUntil(Instant.now().plusSeconds(300));
        notification.setClaimedBy(CLAIM_OWNER);
        return notification;
    }

    private void assertClaimCleared(Notification notification) {
        assertNull(notification.getClaimedAt());
        assertNull(notification.getLockedUntil());
        assertNull(notification.getClaimedBy());
    }
}
