package com.homeenergytracker.insightservice.service;

import com.homeenergytracker.insightservice.dto.AiInsightResult;
import com.homeenergytracker.insightservice.dto.UsageSummaryResponse;
import com.homeenergytracker.insightservice.exception.AiInsightGenerationException;
import com.homeenergytracker.insightservice.prompt.EnergyInsightPromptFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInsightServiceTest {

    private static final String MODEL = "openai/gpt-oss-120b";
    private static final String SYSTEM_PROMPT = "system prompt";
    private static final String USER_PROMPT = "user prompt";
    private static final UsageSummaryResponse USAGE = new UsageSummaryResponse(
            new BigDecimal("12500.50"),
            new BigDecimal("520.25"),
            new BigDecimal("1800.00"),
            Instant.parse("2026-10-01T00:00:00Z"),
            Instant.parse("2026-10-02T00:00:00Z")
    );

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private ChatClient.EntityParamSpec entityParamSpec;

    @Mock
    private EnergyInsightPromptFactory promptFactory;

    private AiInsightService aiInsightService;

    @BeforeEach
    void setUp() {
        aiInsightService = new AiInsightService(
                chatClient,
                promptFactory,
                MODEL
        );
    }

    @Test
    void analyze_shouldUseStructuredOutputAndReturnNormalizedResult() {
        AiInsightResult providerResult = new AiInsightResult(
                "  Energy use was stable.  ",
                Arrays.asList(" Stable load ", null, "", "Stable load", "Peak recorded", "A", "B", "C", "D"),
                Arrays.asList(" Turn devices off ", null, "Turn devices off", "Inspect peak", "A", "B", "C", "D")
        );
        stubChatCall(providerResult);

        AiInsightResult result = aiInsightService.analyze(USAGE);

        assertEquals("Energy use was stable.", result.summary());
        assertEquals(
                List.of("Stable load", "Peak recorded", "A", "B", "C"),
                result.observations()
        );
        assertEquals(
                List.of("Turn devices off", "Inspect peak", "A", "B", "C"),
                result.recommendations()
        );
        assertEquals(MODEL, aiInsightService.model());
        verify(requestSpec).system(SYSTEM_PROMPT);
        verify(requestSpec).user(USER_PROMPT);
        verify(responseSpec).entity(eq(AiInsightResult.class), any());
        verify(entityParamSpec).useProviderStructuredOutput();
        verify(entityParamSpec).validateSchema();
    }

    @Test
    void analyze_shouldConvertNullListsToSafeDefaults() {
        stubChatCall(new AiInsightResult("Valid summary", null, null));

        AiInsightResult result = aiInsightService.analyze(USAGE);

        assertEquals(List.of(), result.observations());
        assertEquals(List.of(), result.recommendations());
    }

    @Test
    void analyze_shouldRejectNullStructuredResult() {
        stubChatCall(null);

        AiInsightGenerationException exception = assertThrows(
                AiInsightGenerationException.class,
                () -> aiInsightService.analyze(USAGE)
        );

        assertEquals("LLM returned an invalid energy insight", exception.getMessage());
    }

    @Test
    void analyze_shouldRejectBlankSummary() {
        stubChatCall(new AiInsightResult(
                "  ",
                List.of("observation"),
                List.of("recommendation")
        ));

        AiInsightGenerationException exception = assertThrows(
                AiInsightGenerationException.class,
                () -> aiInsightService.analyze(USAGE)
        );

        assertEquals("LLM returned an invalid energy insight", exception.getMessage());
    }

    @Test
    void analyze_shouldWrapProviderException() {
        when(chatClient.prompt()).thenThrow(new IllegalStateException("provider unavailable"));

        AiInsightGenerationException exception = assertThrows(
                AiInsightGenerationException.class,
                () -> aiInsightService.analyze(USAGE)
        );

        assertEquals("Could not generate energy insight", exception.getMessage());
        assertEquals("provider unavailable", exception.getCause().getMessage());
    }

    @Test
    void analyze_shouldRejectNullUsageBeforeCallingProvider() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> aiInsightService.analyze(null)
        );

        assertEquals("usage must not be null", exception.getMessage());
        verify(chatClient, never()).prompt();
    }

    @ParameterizedTest
    @MethodSource("invalidMetrics")
    void analyze_shouldRejectNullOrNegativeMetrics(
            UsageSummaryResponse invalidUsage,
            String expectedMessage
    ) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> aiInsightService.analyze(invalidUsage)
        );

        assertEquals(expectedMessage, exception.getMessage());
        verify(chatClient, never()).prompt();
    }

    @Test
    void analyze_shouldRejectMissingPeriodStart() {
        UsageSummaryResponse invalidUsage = usageWithPeriod(null, USAGE.to());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> aiInsightService.analyze(invalidUsage)
        );

        assertEquals("usage.from must not be null", exception.getMessage());
        verify(chatClient, never()).prompt();
    }

    @Test
    void analyze_shouldRejectMissingPeriodEnd() {
        UsageSummaryResponse invalidUsage = usageWithPeriod(USAGE.from(), null);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> aiInsightService.analyze(invalidUsage)
        );

        assertEquals("usage.to must not be null", exception.getMessage());
        verify(chatClient, never()).prompt();
    }

    @Test
    void analyze_shouldRejectPeriodWhoseStartIsNotBeforeEnd() {
        UsageSummaryResponse invalidUsage = usageWithPeriod(USAGE.from(), USAGE.from());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> aiInsightService.analyze(invalidUsage)
        );

        assertEquals("usage.from must be before usage.to", exception.getMessage());
        verify(chatClient, never()).prompt();
    }

    @Test
    void constructor_shouldRejectBlankModel() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new AiInsightService(chatClient, promptFactory, "  ")
        );

        assertEquals("AI model must not be blank", exception.getMessage());
    }

    private void stubChatCall(AiInsightResult result) {
        when(promptFactory.systemPrompt()).thenReturn(SYSTEM_PROMPT);
        when(promptFactory.userPrompt(USAGE)).thenReturn(USER_PROMPT);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.entity(eq(AiInsightResult.class), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Consumer<ChatClient.EntityParamSpec> customizer =
                            invocation.getArgument(1, Consumer.class);
                    when(entityParamSpec.useProviderStructuredOutput())
                            .thenReturn(entityParamSpec);
                    when(entityParamSpec.validateSchema())
                            .thenReturn(entityParamSpec);
                    customizer.accept(entityParamSpec);
                    return result;
                });
    }

    private static Stream<Arguments> invalidMetrics() {
        return Stream.of(
                Arguments.of(
                        usageWithMetrics(null, USAGE.averagePowerW(), USAGE.maxPowerW()),
                        "totalEnergyWh must be non-negative"
                ),
                Arguments.of(
                        usageWithMetrics(new BigDecimal("-0.01"), USAGE.averagePowerW(), USAGE.maxPowerW()),
                        "totalEnergyWh must be non-negative"
                ),
                Arguments.of(
                        usageWithMetrics(USAGE.totalEnergyWh(), null, USAGE.maxPowerW()),
                        "averagePowerW must be non-negative"
                ),
                Arguments.of(
                        usageWithMetrics(USAGE.totalEnergyWh(), new BigDecimal("-0.01"), USAGE.maxPowerW()),
                        "averagePowerW must be non-negative"
                ),
                Arguments.of(
                        usageWithMetrics(USAGE.totalEnergyWh(), USAGE.averagePowerW(), null),
                        "maxPowerW must be non-negative"
                ),
                Arguments.of(
                        usageWithMetrics(USAGE.totalEnergyWh(), USAGE.averagePowerW(), new BigDecimal("-0.01")),
                        "maxPowerW must be non-negative"
                )
        );
    }

    private static UsageSummaryResponse usageWithMetrics(
            BigDecimal totalEnergyWh,
            BigDecimal averagePowerW,
            BigDecimal maxPowerW
    ) {
        return new UsageSummaryResponse(
                totalEnergyWh,
                averagePowerW,
                maxPowerW,
                USAGE.from(),
                USAGE.to()
        );
    }

    private static UsageSummaryResponse usageWithPeriod(
            Instant from,
            Instant to
    ) {
        return new UsageSummaryResponse(
                USAGE.totalEnergyWh(),
                USAGE.averagePowerW(),
                USAGE.maxPowerW(),
                from,
                to
        );
    }
}
