package com.homeenergytracker.usageservice.controller;

import com.homeenergytracker.usageservice.config.SecurityConfig;
import com.homeenergytracker.usageservice.dto.UsageSummaryResponse;
import com.homeenergytracker.usageservice.security.InternalServiceTokenFilter;
import com.homeenergytracker.usageservice.service.EnergyUsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {UsageController.class, InternalUsageController.class},
        properties = "internal.usage-service-token=test-internal-token"
)
@Import(SecurityConfig.class)
class UsageControllerSecurityTest {

    private static final Instant FROM =
            Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO =
            Instant.parse("2026-10-02T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnergyUsageService energyUsageService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void internalSummaryRejectsMissingToken() throws Exception {
        performInternalSummary(null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalSummaryRejectsWrongToken() throws Exception {
        performInternalSummary("wrong-token")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalSummaryUsesPathUserAndMapsResponseForValidToken()
            throws Exception {
        UsageSummaryResponse summary = summary();
        when(energyUsageService.getSummary(42L, FROM, TO))
                .thenReturn(summary);

        performInternalSummary("test-internal-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalEnergyWh").value(12500.5))
                .andExpect(jsonPath("$.data.averagePowerW").value(520.25))
                .andExpect(jsonPath("$.data.maxPowerW").value(1800.0))
                .andExpect(jsonPath("$.data.from").value(FROM.toString()))
                .andExpect(jsonPath("$.data.to").value(TO.toString()));

        verify(energyUsageService).getSummary(42L, FROM, TO);
    }

    @Test
    void internalSummaryRejectsNonPositiveUserIdBeforeCallingService()
            throws Exception {
        mockMvc.perform(get("/internal/v1/usages/users/-1/summary")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .header(
                                InternalServiceTokenFilter.HEADER_NAME,
                                "test-internal-token"
                        ))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(energyUsageService);
    }

    @Test
    void publicSummaryStillAcceptsJwtAndUsesJwtSubject() throws Exception {
        when(energyUsageService.getSummary(42L, FROM, TO))
                .thenReturn(summary());

        mockMvc.perform(get("/api/v1/usages/summary")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .with(jwt().jwt(token -> token.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(energyUsageService).getSummary(42L, FROM, TO);
    }

    @Test
    void publicSummaryStillRejectsMissingJwt() throws Exception {
        mockMvc.perform(get("/api/v1/usages/summary")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions performInternalSummary(
            String token
    ) throws Exception {
        var request = get("/internal/v1/usages/users/42/summary")
                .param("from", FROM.toString())
                .param("to", TO.toString());

        if (token != null) {
            request.header(InternalServiceTokenFilter.HEADER_NAME, token);
        }

        return mockMvc.perform(request);
    }

    private UsageSummaryResponse summary() {
        return new UsageSummaryResponse(
                new BigDecimal("12500.50"),
                new BigDecimal("520.25"),
                new BigDecimal("1800.00"),
                FROM,
                TO
        );
    }
}
