package com.homeenergytracker.notificationservice.repository;

import com.homeenergytracker.notificationservice.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByEventIdAndRuleId(UUID eventId, Long ruleId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    Page<Notification> findAllByUserIdOrderByCreatedAtDescIdDesc(
            Long userId,
            Pageable pageable
    );

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Notification notification
               SET notification.readAt = :readAt
             WHERE notification.userId = :userId
               AND notification.readAt IS NULL
            """)
    int markAllUnreadAsRead(
            @Param("userId") Long userId,
            @Param("readAt") Instant readAt
    );

    @Query(value = """
            SELECT *
              FROM notifications
             WHERE delivery_attempts < :maxAttempts
               AND (
                    (
                        delivery_status IN ('PENDING', 'RETRYING')
                        AND next_attempt_at <= :now
                    )
                    OR
                    (
                        delivery_status = 'PROCESSING'
                        AND locked_until <= :now
                    )
               )
             ORDER BY COALESCE(next_attempt_at, locked_until), id
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Notification> findClaimableForUpdate(
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("batchSize") int batchSize
    );
}
