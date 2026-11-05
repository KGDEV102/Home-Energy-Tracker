package com.homeenergytracker.notificationservice.delivery;

import com.homeenergytracker.notificationservice.entity.DeliveryStatus;
import com.homeenergytracker.notificationservice.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationClaimService {
    private final NotificationRepository notificationRepository;
    private final long claimLeaseMillis;

    public NotificationClaimService(
            NotificationRepository notificationRepository,
            @Value("${notification.delivery.claim-lease-ms}") long claimLeaseMillis
    ) {
        if (claimLeaseMillis < 1) {
            throw new IllegalArgumentException(
                    "notification.delivery.claim-lease-ms must be at least 1"
            );
        }
        this.notificationRepository = notificationRepository;
        this.claimLeaseMillis = claimLeaseMillis;
    }

    @Transactional
    public List<Long> claimDueNotifications(
            String workerId,
            int maxAttempts,
            int batchSize
    ) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }

        Instant now = Instant.now();
        Instant lockedUntil = now.plusMillis(claimLeaseMillis);
        List<Long> claimedIds = new ArrayList<>();

        notificationRepository.findClaimableForUpdate(
                now,
                maxAttempts,
                batchSize
        ).forEach(notification -> {
            notification.setDeliveryStatus(DeliveryStatus.PROCESSING);
            notification.setClaimedAt(now);
            notification.setLockedUntil(lockedUntil);
            notification.setClaimedBy(workerId);
            claimedIds.add(notification.getId());
        });

        return List.copyOf(claimedIds);
    }
}
