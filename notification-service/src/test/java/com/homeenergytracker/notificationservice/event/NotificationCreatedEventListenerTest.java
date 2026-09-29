package com.homeenergytracker.notificationservice.event;

import com.homeenergytracker.notificationservice.mail.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationCreatedEventListenerTest {

    private static final Long NOTIFICATION_ID = 100L;

    @Mock
    private MailService mailService;

    @InjectMocks
    private NotificationCreatedEventListener listener;

    @Test
    void shouldSendNotificationEmail() {
        NotificationCreatedEvent event =
                new NotificationCreatedEvent(NOTIFICATION_ID);

        listener.handle(event);

        verify(mailService).sendNotification(NOTIFICATION_ID);
    }

    @Test
    void shouldPropagateMailServiceException() {
        NotificationCreatedEvent event =
                new NotificationCreatedEvent(NOTIFICATION_ID);
        RuntimeException expected = new RuntimeException("mail failed");
        doThrow(expected)
                .when(mailService)
                .sendNotification(NOTIFICATION_ID);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> listener.handle(event)
        );

        assertSame(expected, actual);
        verify(mailService).sendNotification(NOTIFICATION_ID);
    }
}
