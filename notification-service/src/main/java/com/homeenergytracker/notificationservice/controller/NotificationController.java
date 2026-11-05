package com.homeenergytracker.notificationservice.controller;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.common.security.JwtUserIdExtractor;
import com.homeenergytracker.notificationservice.dto.NotificationResponse;
import com.homeenergytracker.notificationservice.dto.PageResponse;
import com.homeenergytracker.notificationservice.dto.UnreadNotificationCountResponse;
import com.homeenergytracker.notificationservice.service.NotificationInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationInboxService notificationInboxService;

    @GetMapping
    public SuccessResponse<PageResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return SuccessResponse.of(notificationInboxService.getNotifications(
                JwtUserIdExtractor.extract(jwt),
                page,
                size
        ));
    }

    @GetMapping("/unread-count")
    public SuccessResponse<UnreadNotificationCountResponse> getUnreadCount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return SuccessResponse.of(notificationInboxService.getUnreadCount(
                JwtUserIdExtractor.extract(jwt)
        ));
    }

    @GetMapping("/{id}")
    public SuccessResponse<NotificationResponse> getNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return SuccessResponse.of(notificationInboxService.getNotification(
                id,
                JwtUserIdExtractor.extract(jwt)
        ));
    }

    @PatchMapping("/{id}/read")
    public SuccessResponse<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        notificationInboxService.markAsRead(id, JwtUserIdExtractor.extract(jwt));
        return SuccessResponse.of(null, "Notification marked as read");
    }

    @PatchMapping("/read-all")
    public SuccessResponse<Integer> markAllAsRead(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return SuccessResponse.of(notificationInboxService.markAllAsRead(
                JwtUserIdExtractor.extract(jwt)
        ));
    }
}
