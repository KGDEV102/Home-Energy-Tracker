package com.homeenergytracker.notificationservice.service;

import com.homeenergytracker.notificationservice.dto.CreateNotificationRule;
import com.homeenergytracker.notificationservice.dto.PageResponse;
import com.homeenergytracker.notificationservice.dto.UpdateNotificationRule;
import com.homeenergytracker.notificationservice.entity.NotificationRule;
import com.homeenergytracker.notificationservice.exception.NotificationRuleNotExist;
import com.homeenergytracker.notificationservice.mapper.NotificationRuleMapper;
import com.homeenergytracker.notificationservice.repository.NotificationRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationRuleService {
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 20;

    private final NotificationRuleRepository notificationRuleRepository;
    private final NotificationRuleMapper notificationRuleMapper;

    public NotificationRuleService(NotificationRuleRepository notificationRuleRepository, NotificationRuleMapper notificationRuleMapper) {
        this.notificationRuleRepository = notificationRuleRepository;
        this.notificationRuleMapper = notificationRuleMapper;
    }

    @Transactional
    public NotificationRule createNotificationRule(CreateNotificationRule notificationRule, Long userId) {
        NotificationRule notificationRuleEntity = notificationRuleMapper.toEntity(notificationRule);
        notificationRuleEntity.setUserId(userId);
        return notificationRuleRepository.save(notificationRuleEntity);
    }

    @Transactional
    public NotificationRule updateNotificationRule(UpdateNotificationRule updateNotificationRule, Long id, Long userId) {
        NotificationRule notificationRule = notificationRuleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(NotificationRuleNotExist::new);
        notificationRuleMapper.updateEntity(updateNotificationRule, notificationRule);
        return notificationRule;
    }

    @Transactional
    public void deleteNotificationRule(Long id, Long userId) {
        NotificationRule notificationRule = notificationRuleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(NotificationRuleNotExist::new);
        notificationRuleRepository.delete(notificationRule);
    }

    @Transactional(readOnly = true)
    public NotificationRule getNotificationRule(Long id, Long userId) {
        return notificationRuleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(NotificationRuleNotExist::new);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationRule> getAllNotificationRules(
        Long userId,
        int page,
        int size
    ) {
        int safePage = normalizedPage(page);
        int safeSize = normalizedSize(size);
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize
        );
        Page<NotificationRule> notificationRulePage = notificationRuleRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        return new PageResponse<>(
                notificationRulePage.getContent(),
                notificationRulePage.getNumber(),
                notificationRulePage.getSize(),
                notificationRulePage.getTotalPages(),
                notificationRulePage.isFirst(),
                notificationRulePage.isLast(),
                notificationRulePage.hasNext(),
                notificationRulePage.hasPrevious()
        );
    }

    private int normalizedPage(int page){
        return Math.max(0, page);
    }

    private int normalizedSize(int size){
        if(size <=0){
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
