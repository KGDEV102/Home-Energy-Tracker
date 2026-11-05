package com.homeenergytracker.notificationservice.delivery;

import com.homeenergytracker.notificationservice.entity.DeliveryStatus;
import com.homeenergytracker.notificationservice.entity.Notification;
import com.homeenergytracker.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationClaimServiceTest {
    private static final long LEASE_MILLIS = 300_000;

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    void marksSelectedNotificationsAsProcessingAndOwnedByWorker() {
        Notification first = notification(1L, DeliveryStatus.PENDING);
        Notification second = notification(2L, DeliveryStatus.PROCESSING);
        when(notificationRepository.findClaimableForUpdate(
                any(Instant.class),
                eq(5),
                eq(50)
        )).thenReturn(List.of(first, second));

        NotificationClaimService service = new NotificationClaimService(
                notificationRepository,
                LEASE_MILLIS
        );

        List<Long> claimedIds = service.claimDueNotifications(
                "worker-a",
                5,
                50
        );

        assertThat(claimedIds).containsExactly(1L, 2L);
        assertClaim(first);
        assertClaim(second);

        ArgumentCaptor<Instant> nowCaptor =
                ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).findClaimableForUpdate(
                nowCaptor.capture(),
                eq(5),
                eq(50)
        );
        assertThat(first.getClaimedAt()).isEqualTo(nowCaptor.getValue());
        assertThat(first.getLockedUntil())
                .isEqualTo(nowCaptor.getValue().plusMillis(LEASE_MILLIS));
    }

    private void assertClaim(Notification notification) {
        assertThat(notification.getDeliveryStatus())
                .isEqualTo(DeliveryStatus.PROCESSING);
        assertThat(notification.getClaimedBy()).isEqualTo("worker-a");
        assertThat(notification.getClaimedAt()).isNotNull();
        assertThat(notification.getLockedUntil()).isNotNull();
    }

    private Notification notification(Long id, DeliveryStatus status) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setDeliveryStatus(status);
        return notification;
    }
}
