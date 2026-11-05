package com.homeenergytracker.insightservice.prompt;

import com.homeenergytracker.insightservice.dto.UsageSummaryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyInsightPromptFactoryTest {

    private final EnergyInsightPromptFactory promptFactory =
            new EnergyInsightPromptFactory();

    @Test
    void systemPrompt_shouldConstrainTheModelToSuppliedMeasurements() {
        String prompt = promptFactory.systemPrompt();

        assertAll(
                () -> assertTrue(prompt.contains("Never invent")),
                () -> assertTrue(prompt.contains("at most five")),
                () -> assertTrue(prompt.contains("data is insufficient"))
        );
    }

    @Test
    void userPrompt_shouldContainEveryUsageValueAndUnit() {
        UsageSummaryResponse usage = new UsageSummaryResponse(
                new BigDecimal("12500.50"),
                new BigDecimal("520.25"),
                new BigDecimal("1800.00"),
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z")
        );

        String prompt = promptFactory.userPrompt(usage);

        assertAll(
                () -> assertTrue(prompt.contains("2026-10-01T00:00:00Z")),
                () -> assertTrue(prompt.contains("2026-10-02T00:00:00Z")),
                () -> assertTrue(prompt.contains("12500.50 Wh")),
                () -> assertTrue(prompt.contains("520.25 W")),
                () -> assertTrue(prompt.contains("1800.00 W")),
                () -> assertTrue(prompt.contains("without assuming which appliances"))
        );
    }
}
