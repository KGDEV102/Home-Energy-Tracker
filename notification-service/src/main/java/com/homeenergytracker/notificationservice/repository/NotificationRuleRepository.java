package com.homeenergytracker.notificationservice.repository;

import com.homeenergytracker.notificationservice.entity.NotificationRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationRuleRepository extends JpaRepository<NotificationRule,Long> {
    Page<NotificationRule> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Optional<NotificationRule> findByIdAndUserId(Long id, Long userId);
}
