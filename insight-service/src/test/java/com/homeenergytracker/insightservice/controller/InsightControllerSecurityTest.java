package com.homeenergytracker.insightservice.controller;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.insightservice.config.SecurityConfig;
import com.homeenergytracker.insightservice.dto.EnergyInsightResponse;
import com.homeenergytracker.insightservice.dto.InsightSeverity;
import com.homeenergytracker.insightservice.service.EnergyInsightService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsightController.class)
@Import(SecurityConfig.class)
class InsightControllerSecurityTest {

    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnergyInsightService energyInsightService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void generateRejectsMissingJwt() throws Exception {
        mockMvc.perform(generateRequest())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generateRejectsMalformedBearerToken() throws Exception {
        when(jwtDecoder.decode("not-a-jwt"))
                .thenThrow(new BadJwtException("invalid token"));

        mockMvc.perform(generateRequest()
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(energyInsightService);
    }

    @Test
    void generateUsesAuthenticatedJwtSubject() throws Exception {
        when(energyInsightService.generate(42L, FROM, TO)).thenReturn(response());

        mockMvc.perform(generateRequest().with(jwt().jwt(token -> token.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary").value("Usage is stable."));

        verify(energyInsightService).generate(eq(42L), eq(FROM), eq(TO));
    }

    @Test
    void generateIgnoresUserIdSuppliedByClient() throws Exception {
        when(energyInsightService.generate(42L, FROM, TO)).thenReturn(response());

        mockMvc.perform(post("/api/v1/insights/generate")
                        .with(jwt().jwt(token -> token.subject("42")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 999,
                                  "from": "2026-10-01T00:00:00Z",
                                  "to": "2026-10-02T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk());

        verify(energyInsightService).generate(42L, FROM, TO);
    }

    @Test
    void generateRejectsMissingTime() throws Exception {
        mockMvc.perform(post("/api/v1/insights/generate")
                        .with(jwt().jwt(token -> token.subject("42")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from": "2026-10-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(energyInsightService);
    }

    @Test
    void generateRejectsNonIncreasingTimeRange() throws Exception {
        mockMvc.perform(post("/api/v1/insights/generate")
                        .with(jwt().jwt(token -> token.subject("42")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "from": "2026-10-02T00:00:00Z",
                                  "to": "2026-10-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(energyInsightService);
    }

    @Test
    void generateRejectsInvalidJwtSubject() throws Exception {
        mockMvc.perform(generateRequest()
                        .with(jwt().jwt(token -> token.subject("not-a-user-id"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(energyInsightService);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder generateRequest() {
        return post("/api/v1/insights/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "from": "2026-10-01T00:00:00Z",
                          "to": "2026-10-02T00:00:00Z"
                        }
                        """);
    }

    private EnergyInsightResponse response() {
        return new EnergyInsightResponse(
                FROM, TO,
                new BigDecimal("12500"), new BigDecimal("520"), new BigDecimal("1800"),
                "Usage is stable.", List.of(), List.of(), InsightSeverity.LOW, "test-model"
        );
    }
}
