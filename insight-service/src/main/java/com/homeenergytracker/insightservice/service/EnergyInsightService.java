package com.homeenergytracker.insightservice.service;

import com.homeenergytracker.insightservice.dto.AiInsightResult;
import com.homeenergytracker.insightservice.dto.EnergyInsightResponse;
import com.homeenergytracker.insightservice.dto.InsightSeverity;
import com.homeenergytracker.insightservice.dto.UsageSummaryResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class EnergyInsightService {

    private final UsageQueryService usageQueryService;
    private final AiInsightService aiInsightService;

    public EnergyInsightService(
            UsageQueryService usageQueryService,
            AiInsightService aiInsightService
    ) {
        this.usageQueryService = usageQueryService;
        this.aiInsightService = aiInsightService;
    }

    public EnergyInsightResponse generate(
            Long userId,
            Instant from,
            Instant to
    ) {
        UsageSummaryResponse usage = usageQueryService.getSummary(
                userId,
                from,
                to
        );

        if (hasNoMeasuredUsage(usage)) {
            return new EnergyInsightResponse(
                    usage.from(),
                    usage.to(),
                    usage.totalEnergyWh(),
                    usage.averagePowerW(),
                    usage.maxPowerW(),
                    "No measurable energy usage was recorded for this period.",
                    List.of("All supplied energy and power measurements are zero."),
                    List.of(),
                    InsightSeverity.UNKNOWN,
                    null
            );
        }

        AiInsightResult insight = aiInsightService.analyze(usage);

        return new EnergyInsightResponse(
                usage.from(),
                usage.to(),
                usage.totalEnergyWh(),
                usage.averagePowerW(),
                usage.maxPowerW(),
                insight.summary(),
                insight.observations(),
                insight.recommendations(),
                InsightSeverity.UNKNOWN,
                aiInsightService.model()
        );
    }

    private boolean hasNoMeasuredUsage(UsageSummaryResponse usage) {
        return usage.totalEnergyWh() != null
                && usage.averagePowerW() != null
                && usage.maxPowerW() != null
                && usage.totalEnergyWh().signum() == 0
                && usage.averagePowerW().signum() == 0
                && usage.maxPowerW().signum() == 0;
    }
}
