package com.homeenergytracker.notificationservice.service;

import com.homeenergytracker.notificationservice.dto.NotificationResponse;
import com.homeenergytracker.notificationservice.dto.PageResponse;
import com.homeenergytracker.notificationservice.dto.UnreadNotificationCountResponse;
import com.homeenergytracker.notificationservice.entity.Notification;
import com.homeenergytracker.notificationservice.exception.NotificationNotFound;
import com.homeenergytracker.notificationservice.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class NotificationInboxService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final NotificationRepository notificationRepository;

    public NotificationInboxService(
            NotificationRepository notificationRepository
    ) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(
            Long userId,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                normalizeSize(size)
        );
        Page<NotificationResponse> notificationPage = notificationRepository
                .findAllByUserIdOrderByCreatedAtDescIdDesc(userId, pageable)
                .map(this::toResponse);

        return new PageResponse<>(
                notificationPage.getContent(),
                notificationPage.getNumber(),
                notificationPage.getSize(),
                notificationPage.getTotalPages(),
                notificationPage.isFirst(),
                notificationPage.isLast(),
                notificationPage.hasNext(),
                notificationPage.hasPrevious()
        );
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotification(Long id, Long userId) {
        return notificationRepository.findByIdAndUserId(id, userId)
                .map(this::toResponse)
                .orElseThrow(NotificationNotFound::new);
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse getUnreadCount(Long userId) {
        return new UnreadNotificationCountResponse(
                notificationRepository.countByUserIdAndReadAtIsNull(userId)
        );
    }

    @Transactional
    public void markAsRead(Long id, Long userId) {
        Notification notification = notificationRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(NotificationNotFound::new);

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllUnreadAsRead(
                userId,
                Instant.now()
        );
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getDeviceUid(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getTriggerValue(),
                notification.getThresholdValue(),
                notification.getMeasuredAt(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getReadAt() != null
        );
    }
}
