package com.homeenergytracker.notificationservice.delivery;

import com.homeenergytracker.notificationservice.mail.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryWorkerTest {
    private static final String WORKER_ID = "worker-a";

    @Mock
    private NotificationClaimService notificationClaimService;

    @Mock
    private MailService mailService;

    @Test
    void deliversOnlyNotificationsClaimedByThisWorker() {
        when(notificationClaimService.claimDueNotifications(
                WORKER_ID,
                5,
                50
        )).thenReturn(List.of(1L, 2L));

        NotificationDeliveryWorker worker = worker();

        worker.deliverDueNotifications();

        verify(mailService).sendNotification(1L, WORKER_ID);
        verify(mailService).sendNotification(2L, WORKER_ID);
    }

    @Test
    void continuesProcessingWhenOneDeliveryThrowsUnexpectedly() {
        when(notificationClaimService.claimDueNotifications(
                WORKER_ID,
                5,
                50
        )).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("unexpected failure"))
                .when(mailService).sendNotification(1L, WORKER_ID);

        NotificationDeliveryWorker worker = worker();

        worker.deliverDueNotifications();

        verify(mailService).sendNotification(1L, WORKER_ID);
        verify(mailService).sendNotification(2L, WORKER_ID);
    }

    private NotificationDeliveryWorker worker() {
        return new NotificationDeliveryWorker(
                notificationClaimService,
                mailService,
                5,
                50,
                WORKER_ID
        );
    }
}
