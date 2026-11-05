package com.homeenergytracker.insightservice.dto;

import java.util.List;

public record AiInsightResult(
        String summary,
        List<String> observations,
        List<String> recommendations
) {
}
