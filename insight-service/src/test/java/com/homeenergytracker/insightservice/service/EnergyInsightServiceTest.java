package com.homeenergytracker.insightservice.service;

import com.homeenergytracker.insightservice.dto.AiInsightResult;
import com.homeenergytracker.insightservice.dto.EnergyInsightResponse;
import com.homeenergytracker.insightservice.dto.InsightSeverity;
import com.homeenergytracker.insightservice.dto.UsageSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnergyInsightServiceTest {

    private static final Long USER_ID = 42L;
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");

    @Mock
    private UsageQueryService usageQueryService;

    @Mock
    private AiInsightService aiInsightService;

    private EnergyInsightService energyInsightService;

    @BeforeEach
    void setUp() {
        energyInsightService = new EnergyInsightService(
                usageQueryService,
                aiInsightService
        );
    }

    @Test
    void generate_shouldMapUsageAndAiResultToResponse() {
        UsageSummaryResponse usage = new UsageSummaryResponse(
                new BigDecimal("12500.50"),
                new BigDecimal("520.25"),
                new BigDecimal("1800.00"),
                FROM,
                TO
        );
        AiInsightResult insight = new AiInsightResult(
                "Energy use was stable.",
                List.of("Peak power was 1800 W."),
                List.of("Inspect high-power periods.")
        );

        when(usageQueryService.getSummary(USER_ID, FROM, TO)).thenReturn(usage);
        when(aiInsightService.analyze(usage)).thenReturn(insight);
        when(aiInsightService.model()).thenReturn("openai/gpt-oss-120b");

        EnergyInsightResponse response = energyInsightService.generate(
                USER_ID,
                FROM,
                TO
        );

        assertAll(
                () -> assertEquals(FROM, response.from()),
                () -> assertEquals(TO, response.to()),
                () -> assertEquals(new BigDecimal("12500.50"), response.totalEnergyWh()),
                () -> assertEquals(new BigDecimal("520.25"), response.averagePowerW()),
                () -> assertEquals(new BigDecimal("1800.00"), response.maxPowerW()),
                () -> assertEquals("Energy use was stable.", response.summary()),
                () -> assertEquals(insight.observations(), response.observations()),
                () -> assertEquals(insight.recommendations(), response.recommendations()),
                () -> assertEquals(InsightSeverity.UNKNOWN, response.severity()),
                () -> assertEquals("openai/gpt-oss-120b", response.model())
        );
        verify(usageQueryService).getSummary(USER_ID, FROM, TO);
        verify(aiInsightService).analyze(usage);
    }

    @Test
    void generate_shouldReturnNoDataResponseWithoutCallingAi() {
        UsageSummaryResponse usage = new UsageSummaryResponse(
                BigDecimal.ZERO,
                new BigDecimal("0.00"),
                BigDecimal.ZERO,
                FROM,
                TO
        );
        when(usageQueryService.getSummary(USER_ID, FROM, TO)).thenReturn(usage);

        EnergyInsightResponse response = energyInsightService.generate(
                USER_ID,
                FROM,
                TO
        );

        assertAll(
                () -> assertEquals(
                        "No measurable energy usage was recorded for this period.",
                        response.summary()
                ),
                () -> assertEquals(
                        List.of("All supplied energy and power measurements are zero."),
                        response.observations()
                ),
                () -> assertEquals(List.of(), response.recommendations()),
                () -> assertEquals(InsightSeverity.UNKNOWN, response.severity()),
                () -> assertNull(response.model())
        );
        verify(usageQueryService).getSummary(USER_ID, FROM, TO);
        verify(aiInsightService, never()).analyze(usage);
        verify(aiInsightService, never()).model();
    }
}
