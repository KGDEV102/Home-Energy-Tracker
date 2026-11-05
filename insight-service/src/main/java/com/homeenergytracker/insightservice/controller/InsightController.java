package com.homeenergytracker.insightservice.controller;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.common.security.JwtUserIdExtractor;
import com.homeenergytracker.insightservice.dto.EnergyInsightResponse;
import com.homeenergytracker.insightservice.dto.GenerateEnergyInsightRequest;
import com.homeenergytracker.insightservice.service.EnergyInsightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
public class InsightController {

    private final EnergyInsightService energyInsightService;

    @PostMapping("/generate")
    public SuccessResponse<EnergyInsightResponse> generate(
            @Valid @RequestBody GenerateEnergyInsightRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return SuccessResponse.of(energyInsightService.generate(
                JwtUserIdExtractor.extract(jwt),
                request.from(),
                request.to()
        ));
    }
}
