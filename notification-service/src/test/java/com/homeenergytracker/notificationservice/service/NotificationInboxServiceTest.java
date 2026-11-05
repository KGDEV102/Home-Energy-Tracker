package com.homeenergytracker.notificationservice.service;

import com.homeenergytracker.notificationservice.dto.NotificationResponse;
import com.homeenergytracker.notificationservice.dto.PageResponse;
import com.homeenergytracker.notificationservice.dto.UnreadNotificationCountResponse;
import com.homeenergytracker.notificationservice.entity.Notification;
import com.homeenergytracker.notificationservice.entity.NotificationType;
import com.homeenergytracker.notificationservice.exception.NotificationNotFound;
import com.homeenergytracker.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceTest {
    private static final Long USER_ID = 1L;
    private static final Long NOTIFICATION_ID = 10L;

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationInboxService service;

    @BeforeEach
    void setUp() {
        service = new NotificationInboxService(notificationRepository);
    }

    @Test
    void returnsOnlyAuthenticatedUsersNotificationsWithSafePaging() {
        Notification notification = notification();
        PageRequest expectedPageable = PageRequest.of(0, 20);
        when(notificationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(
                USER_ID,
                expectedPageable
        )).thenReturn(new PageImpl<>(
                List.of(notification),
                expectedPageable,
                1
        ));

        PageResponse<NotificationResponse> response =
                service.getNotifications(USER_ID, -1, 0);

        assertEquals(1, response.items().size());
        assertFalse(response.items().getFirst().read());
        assertEquals(0, response.currentPage());
        assertEquals(20, response.pageSize());
        verify(notificationRepository).findAllByUserIdOrderByCreatedAtDescIdDesc(
                USER_ID,
                expectedPageable
        );
    }

    @Test
    void returnsNotificationOnlyWhenOwnedByAuthenticatedUser() {
        Notification notification = notification();
        when(notificationRepository.findByIdAndUserId(
                NOTIFICATION_ID,
                USER_ID
        )).thenReturn(Optional.of(notification));

        NotificationResponse response = service.getNotification(
                NOTIFICATION_ID,
                USER_ID
        );

        assertEquals(NOTIFICATION_ID, response.id());
        assertFalse(response.read());
    }

    @Test
    void hidesNotificationNotOwnedByAuthenticatedUser() {
        when(notificationRepository.findByIdAndUserId(
                NOTIFICATION_ID,
                USER_ID
        )).thenReturn(Optional.empty());

        assertThrows(
                NotificationNotFound.class,
                () -> service.getNotification(NOTIFICATION_ID, USER_ID)
        );
    }

    @Test
    void marksUnreadNotificationAsRead() {
        Notification notification = notification();
        when(notificationRepository.findByIdAndUserId(
                NOTIFICATION_ID,
                USER_ID
        )).thenReturn(Optional.of(notification));

        service.markAsRead(NOTIFICATION_ID, USER_ID);

        assertNotNull(notification.getReadAt());
    }

    @Test
    void returnsUnreadCountForAuthenticatedUser() {
        when(notificationRepository.countByUserIdAndReadAtIsNull(USER_ID))
                .thenReturn(3L);

        UnreadNotificationCountResponse response =
                service.getUnreadCount(USER_ID);

        assertEquals(3L, response.count());
    }

    @Test
    void marksAllUnreadNotificationsAsRead() {
        when(notificationRepository.markAllUnreadAsRead(
                eq(USER_ID),
                any(Instant.class)
        )).thenReturn(4);

        int updatedCount = service.markAllAsRead(USER_ID);

        assertEquals(4, updatedCount);
        ArgumentCaptor<Instant> readAtCaptor =
                ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).markAllUnreadAsRead(
                eq(USER_ID),
                readAtCaptor.capture()
        );
        assertNotNull(readAtCaptor.getValue());
    }

    private Notification notification() {
        Notification notification = new Notification();
        notification.setId(NOTIFICATION_ID);
        notification.setUserId(USER_ID);
        notification.setDeviceUid(UUID.randomUUID());
        notification.setType(NotificationType.POWER_THRESHOLD);
        notification.setTitle("Power threshold exceeded");
        notification.setMessage("Power exceeded 1000 W");
        notification.setTriggerValue(new BigDecimal("1200.000"));
        notification.setThresholdValue(new BigDecimal("1000.000"));
        notification.setMeasuredAt(Instant.parse("2026-10-13T00:00:00Z"));
        notification.setCreatedAt(Instant.parse("2026-10-13T00:01:00Z"));
        return notification;
    }
}
