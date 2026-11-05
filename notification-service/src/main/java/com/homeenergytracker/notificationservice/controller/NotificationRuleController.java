package com.homeenergytracker.notificationservice.controller;

import com.homeenergytracker.notificationservice.dto.CreateNotificationRule;
import com.homeenergytracker.notificationservice.dto.PageResponse;
import com.homeenergytracker.notificationservice.dto.UpdateNotificationRule;
import com.homeenergytracker.notificationservice.entity.NotificationRule;
import com.homeenergytracker.common.security.JwtUserIdExtractor;
import com.homeenergytracker.notificationservice.service.NotificationRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.homeenergytracker.common.response.SuccessResponse;

@RestController
@RequestMapping("/api/v1/notification-rules")
@RequiredArgsConstructor
public class NotificationRuleController {
    private final NotificationRuleService notificationRuleService;

    @GetMapping
    public SuccessResponse<PageResponse<NotificationRule>> findAllNotificationRuleOfUser(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long userId = JwtUserIdExtractor.extract(jwt);
        return SuccessResponse.of(
                notificationRuleService.getAllNotificationRules(userId, page, size)
        );
    }

    @GetMapping("/{id}")
    public SuccessResponse<NotificationRule> findNotificationRuleById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = JwtUserIdExtractor.extract(jwt);
        return SuccessResponse.of(
                notificationRuleService.getNotificationRule(id, userId)
        );
    }

    @PostMapping
    public SuccessResponse<NotificationRule> createNotificationRule(
            @Valid @RequestBody CreateNotificationRule notificationRule,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = JwtUserIdExtractor.extract(jwt);
        return SuccessResponse.of(
                notificationRuleService.createNotificationRule(notificationRule, userId)
        );
    }

    @PatchMapping("/{id}")
    public SuccessResponse<NotificationRule> updateNotificationRule(
            @Valid @RequestBody UpdateNotificationRule notificationRule,
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = JwtUserIdExtractor.extract(jwt);
        return SuccessResponse.of(
                notificationRuleService.updateNotificationRule(notificationRule, id, userId)
        );
    }

    @DeleteMapping("/{id}")
    public SuccessResponse<Void> deleteNotificationRule(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = JwtUserIdExtractor.extract(jwt);
        notificationRuleService.deleteNotificationRule(id, userId);
        return SuccessResponse.of(
                null,
                "Delete notification rule id " + id + " successfully"
        );
    }
}
