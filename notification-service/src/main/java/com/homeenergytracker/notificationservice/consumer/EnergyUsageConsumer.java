package com.homeenergytracker.notificationservice.consumer;

import com.homeenergytracker.common.event.EnergyUsageEvent;
import com.homeenergytracker.notificationservice.service.NotificationEvaluationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EnergyUsageConsumer {
    private final NotificationEvaluationService notificationEvaluationService;

    public EnergyUsageConsumer(
            NotificationEvaluationService notificationEvaluationService
    ) {
        this.notificationEvaluationService = notificationEvaluationService;
    }

    @KafkaListener(
            topics = "${notification.kafka.energy-usage-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(EnergyUsageEvent event) {
        notificationEvaluationService.evaluate(event);
    }
}
