package com.homeenergytracker.usageservice.exception;

public class InvalidEnergyUsageEventException extends com.homeenergytracker.common.exception.BusinessException {
    public InvalidEnergyUsageEventException(String message) {
        super(
                "INVALID_ENERGY_USAGE_EVENT",
                message
        );
    }
}
