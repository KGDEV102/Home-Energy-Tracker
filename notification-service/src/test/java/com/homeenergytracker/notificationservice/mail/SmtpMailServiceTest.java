package com.homeenergytracker.notificationservice.mail;

import com.homeenergytracker.notificationservice.dto.UserNotificationContactResponse;
import com.homeenergytracker.notificationservice.entity.DeliveryStatus;
import com.homeenergytracker.notificationservice.entity.Notification;
import com.homeenergytracker.notificationservice.entity.NotificationType;
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
import org.springframework.mail.javamail.JavaMailSender;

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

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserContactService userContactService;

    private SmtpMailService service;

    @BeforeEach
    void setUp() {
        service = new SmtpMailService(
                mailSender,
                notificationRepository,
                userContactService,
                FROM_ADDRESS
        );
    }

    @Test
    void shouldSendEmailAndMarkNotificationAsSent() {
        Notification notification = pendingNotification();

        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));
        when(userContactService.getNotificationContact(USER_ID))
                .thenReturn(validContact());

        service.sendNotification(NOTIFICATION_ID);

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

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
    }

    @Test
    void shouldNotSendEmailWhenNotificationWasAlreadySent() {
        Notification notification = pendingNotification();
        notification.setDeliveryStatus(DeliveryStatus.SENT);
        notification.setDeliveryAttempts(1);

        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));

        service.sendNotification(NOTIFICATION_ID);

        verifyNoInteractions(userContactService);
        verifyNoInteractions(mailSender);
        assertEquals(1, notification.getDeliveryAttempts());
    }

    @Test
    void shouldThrowWhenNotificationDoesNotExist() {
        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.sendNotification(NOTIFICATION_ID)
        );

        assertEquals(
                "Notification not found: 100",
                exception.getMessage()
        );
        verifyNoInteractions(userContactService);
        verifyNoInteractions(mailSender);
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

        service.sendNotification(NOTIFICATION_ID);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        assertEquals(DeliveryStatus.FAILED, notification.getDeliveryStatus());
        assertEquals(1, notification.getDeliveryAttempts());
        assertNull(notification.getRecipientEmail());
        assertNull(notification.getSentAt());
        assertEquals(
                "Could not load notification contact",
                notification.getLastDeliveryError()
        );
    }

    @Test
    void shouldMarkNotificationAsFailedWhenSmtpSendFails() {
        Notification notification = pendingNotification();

        when(notificationRepository.findById(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification));
        when(userContactService.getNotificationContact(USER_ID))
                .thenReturn(validContact());
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        service.sendNotification(NOTIFICATION_ID);

        verify(mailSender).send(any(SimpleMailMessage.class));
        assertEquals(DeliveryStatus.FAILED, notification.getDeliveryStatus());
        assertEquals(1, notification.getDeliveryAttempts());
        assertEquals(RECIPIENT_EMAIL, notification.getRecipientEmail());
        assertNull(notification.getSentAt());
        assertEquals(
                "SMTP unavailable",
                notification.getLastDeliveryError()
        );
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
        notification.setDeliveryStatus(DeliveryStatus.PENDING);
        notification.setDeliveryAttempts(0);
        return notification;
    }
}
