package com.homeenergytracker.usageservice.validation;

import com.homeenergytracker.usageservice.event.EnergyUsageEvent;
import com.homeenergytracker.usageservice.exception.InvalidEnergyUsageEventException;
import org.springframework.stereotype.Component;

@Component
public class EnergyUsageEventValidator {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    public void validate(
            String kafkaKey,
            EnergyUsageEvent event
    ) {
        if (event == null) {
            throw invalid("Event must not be null");
        }

        if (event.eventId() == null) {
            throw invalid("eventId is required");
        }

        if (event.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported schemaVersion: "
                            + event.schemaVersion()
            );
        }

        if (event.deviceUid() == null) {
            throw invalid("deviceUid is required");
        }

        if (kafkaKey == null || kafkaKey.isBlank()) {
            throw invalid("Kafka key is required");
        }

        if (!event.deviceUid().toString().equals(kafkaKey)) {
            throw invalid(
                    "Kafka key does not match deviceUid"
            );
        }

        if (event.userId() == null) {
            throw invalid("userId is required");
        }

        if (event.deviceType() == null
                || event.deviceType().isBlank()) {
            throw invalid("deviceType is required");
        }

        if (event.averagePowerW() == null
                || event.averagePowerW().signum() < 0) {
            throw invalid(
                    "averagePowerW must be greater than or equal to zero"
            );
        }

        if (event.intervalEnergyWh() == null
                || event.intervalEnergyWh().signum() < 0) {
            throw invalid(
                    "intervalEnergyWh must be greater than or equal to zero"
            );
        }

        if (event.measuredAt() == null) {
            throw invalid("measuredAt is required");
        }

        if (event.receivedAt() == null) {
            throw invalid("receivedAt is required");
        }

        if (event.receivedAt().isBefore(event.measuredAt())) {
            throw invalid(
                    "receivedAt must not be before measuredAt"
            );
        }

        if (event.source() == null) {
            throw invalid("source is required");
        }
    }

    private InvalidEnergyUsageEventException invalid(
            String message
    ) {
        return new InvalidEnergyUsageEventException(message);
    }
}
