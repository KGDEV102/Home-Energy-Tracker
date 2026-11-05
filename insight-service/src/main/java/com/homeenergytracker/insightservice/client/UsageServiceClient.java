package com.homeenergytracker.insightservice.client;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.insightservice.dto.UsageSummaryResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.time.Instant;

@HttpExchange("/internal/v1/usages")
public interface UsageServiceClient {

    @GetExchange("/users/{userId}/summary")
    SuccessResponse<UsageSummaryResponse> getSummary(
            @PathVariable("userId") Long userId,
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to,
            @RequestHeader("X-Internal-Token") String internalToken
    );
}
