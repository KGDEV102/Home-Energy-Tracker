package com.homeenergytracker.usageservice.consumer;

import com.homeenergytracker.usageservice.event.EnergyUsageEvent;
import com.homeenergytracker.usageservice.repository.EnergyUsageRepository;
import com.homeenergytracker.usageservice.validation.EnergyUsageEventValidator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EnergyUsageConsumer {
    private final EnergyUsageEventValidator validator;
    private final EnergyUsageRepository repository;
    public EnergyUsageConsumer(
            EnergyUsageEventValidator validator,
            EnergyUsageRepository repository
    ) {
        this.validator = validator;
        this.repository = repository;
    }
    @KafkaListener(
            topics = "${usage.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(
            ConsumerRecord<String, EnergyUsageEvent> record
    ) {
        EnergyUsageEvent event = record.value();

        validator.validate(record.key(), event);
        repository.save(event);
    }
}
