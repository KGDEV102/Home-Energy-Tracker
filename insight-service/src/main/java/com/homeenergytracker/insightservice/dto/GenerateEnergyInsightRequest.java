package com.homeenergytracker.insightservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

import java.time.Instant;

public record GenerateEnergyInsightRequest(
        @NotNull(message = "from is required")
        Instant from,

        @NotNull(message = "to is required")
        Instant to
) {
    @AssertTrue(message = "from must be before to")
    public boolean isTimeRangeValid() {
        return from == null || to == null || from.isBefore(to);
    }
}
