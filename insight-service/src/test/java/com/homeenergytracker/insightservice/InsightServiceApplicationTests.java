package com.homeenergytracker.insightservice;

import com.homeenergytracker.insightservice.client.UsageServiceClient;
import com.homeenergytracker.insightservice.dto.UsageSummaryResponse;
import com.homeenergytracker.insightservice.exception.AiInsightGenerationException;
import com.homeenergytracker.insightservice.exception.UsageServiceUnavailableException;
import com.homeenergytracker.insightservice.service.AiInsightService;
import com.homeenergytracker.insightservice.service.UsageQueryService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "internal.usage-service-token=test-internal-token",
        "spring.ai.openai.api-key=test-groq-api-key"
})
class InsightServiceApplicationTests {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ChatClient chatClient;

    @MockitoBean
    private UsageServiceClient usageServiceClient;

    @Autowired
    private AiInsightService aiInsightService;

    @Autowired
    private UsageQueryService usageQueryService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void contextLoads() {
    }

    @Test
    void openGroqCircuitBreaker_shouldSkipProviderAndUseFallback() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("groq");
        circuitBreaker.transitionToOpenState();

        try {
            UsageSummaryResponse usage = new UsageSummaryResponse(
                    new BigDecimal("12500.50"),
                    new BigDecimal("520.25"),
                    new BigDecimal("1800.00"),
                    Instant.parse("2026-10-01T00:00:00Z"),
                    Instant.parse("2026-10-02T00:00:00Z")
            );

            AiInsightGenerationException exception = assertThrows(
                    AiInsightGenerationException.class,
                    () -> aiInsightService.analyze(usage)
            );

            assertEquals(
                    "AI service is temporarily unavailable",
                    exception.getMessage()
            );
            assertInstanceOf(
                    CallNotPermittedException.class,
                    exception.getCause()
            );
        } finally {
            circuitBreaker.reset();
        }
    }

    @Test
    void repeatedGroqFailures_shouldOpenCircuitAndRejectNextCall() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("groq");
        circuitBreaker.reset();
        UsageSummaryResponse usage = validUsage();
        when(chatClient.prompt()).thenThrow(
                new IllegalStateException("provider unavailable")
        );

        try {
            for (int attempt = 0; attempt < 5; attempt++) {
                assertThrows(
                        AiInsightGenerationException.class,
                        () -> aiInsightService.analyze(usage)
                );
            }

            assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

            AiInsightGenerationException exception = assertThrows(
                    AiInsightGenerationException.class,
                    () -> aiInsightService.analyze(usage)
            );

            assertInstanceOf(
                    CallNotPermittedException.class,
                    exception.getCause()
            );
            verify(chatClient, times(5)).prompt();
        } finally {
            circuitBreaker.reset();
        }
    }

    @Test
    void invalidUsage_shouldNotAffectGroqCircuitBreakerMetrics() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("groq");
        circuitBreaker.reset();
        UsageSummaryResponse invalidUsage = new UsageSummaryResponse(
                new BigDecimal("-1"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z")
        );

        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> aiInsightService.analyze(invalidUsage)
            );

            assertEquals(
                    0,
                    circuitBreaker.getMetrics().getNumberOfBufferedCalls()
            );
        } finally {
            circuitBreaker.reset();
        }
    }

    @Test
    void repeatedUsageServiceFailures_shouldOpenOnlyUsageCircuitBreaker() {
        CircuitBreaker usageCircuitBreaker =
                circuitBreakerRegistry.circuitBreaker("usageService");
        CircuitBreaker groqCircuitBreaker =
                circuitBreakerRegistry.circuitBreaker("groq");
        usageCircuitBreaker.reset();
        groqCircuitBreaker.reset();
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-02T00:00:00Z");
        when(usageServiceClient.getSummary(
                42L,
                from,
                to,
                "test-internal-token"
        )).thenThrow(new IllegalStateException("usage-service unavailable"));

        try {
            for (int attempt = 0; attempt < 5; attempt++) {
                assertThrows(
                        UsageServiceUnavailableException.class,
                        () -> usageQueryService.getSummary(42L, from, to)
                );
            }

            assertEquals(
                    CircuitBreaker.State.OPEN,
                    usageCircuitBreaker.getState()
            );
            assertEquals(
                    CircuitBreaker.State.CLOSED,
                    groqCircuitBreaker.getState()
            );

            UsageServiceUnavailableException exception = assertThrows(
                    UsageServiceUnavailableException.class,
                    () -> usageQueryService.getSummary(42L, from, to)
            );

            assertInstanceOf(
                    CallNotPermittedException.class,
                    exception.getCause()
            );
            verify(usageServiceClient, times(5)).getSummary(
                    42L,
                    from,
                    to,
                    "test-internal-token"
            );
        } finally {
            usageCircuitBreaker.reset();
            groqCircuitBreaker.reset();
        }
    }

    @Test
    void invalidUsageRequest_shouldNotAffectUsageCircuitBreakerMetrics() {
        CircuitBreaker circuitBreaker =
                circuitBreakerRegistry.circuitBreaker("usageService");
        circuitBreaker.reset();
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-02T00:00:00Z");

        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> usageQueryService.getSummary(-1L, from, to)
            );

            assertEquals(
                    0,
                    circuitBreaker.getMetrics().getNumberOfBufferedCalls()
            );
        } finally {
            circuitBreaker.reset();
        }
    }

    private UsageSummaryResponse validUsage() {
        return new UsageSummaryResponse(
                new BigDecimal("12500.50"),
                new BigDecimal("520.25"),
                new BigDecimal("1800.00"),
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z")
        );
    }

}
