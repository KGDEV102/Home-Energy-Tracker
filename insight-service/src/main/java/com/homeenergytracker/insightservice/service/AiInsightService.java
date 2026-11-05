package com.homeenergytracker.insightservice.service;

import com.homeenergytracker.insightservice.dto.AiInsightResult;
import com.homeenergytracker.insightservice.dto.UsageSummaryResponse;
import com.homeenergytracker.insightservice.exception.AiInsightGenerationException;
import com.homeenergytracker.insightservice.prompt.EnergyInsightPromptFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class AiInsightService {

    private static final int MAX_ITEMS = 5;

    private final ChatClient chatClient;
    private final EnergyInsightPromptFactory promptFactory;
    private final String model;

    public AiInsightService(
            ChatClient chatClient,
            EnergyInsightPromptFactory promptFactory,
            @Value("${spring.ai.openai.chat.model}") String model
    ) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("AI model must not be blank");
        }
        this.chatClient = chatClient;
        this.promptFactory = promptFactory;
        this.model = model;
    }

    @CircuitBreaker(name = "groq", fallbackMethod = "groqFallback")
    public AiInsightResult analyze(UsageSummaryResponse usage) {
        Objects.requireNonNull(usage, "usage must not be null");
        validateUsage(usage);

        try {
            AiInsightResult result = chatClient.prompt()
                    .system(promptFactory.systemPrompt())
                    .user(promptFactory.userPrompt(usage))
                    .call()
                    .entity(
                            AiInsightResult.class,
                            spec -> spec
                                    .useProviderStructuredOutput()
                                    .validateSchema()
                    );

            return normalizeAndValidate(result);
        } catch (AiInsightGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiInsightGenerationException(
                    "Could not generate energy insight",
                    exception
            );
        }
    }

    private AiInsightResult groqFallback(
            UsageSummaryResponse usage,
            Throwable cause
    ) {
        if (cause instanceof IllegalArgumentException exception) {
            throw exception;
        }
        if (cause instanceof NullPointerException exception) {
            throw exception;
        }

        throw new AiInsightGenerationException(
                "AI service is temporarily unavailable",
                cause
        );
    }

    public String model() {
        return model;
    }

    private AiInsightResult normalizeAndValidate(AiInsightResult result) {
        if (result == null
                || result.summary() == null
                || result.summary().isBlank()) {
            throw new AiInsightGenerationException(
                    "LLM returned an invalid energy insight"
            );
        }

        return new AiInsightResult(
                result.summary().trim(),
                normalizeItems(result.observations()),
                normalizeItems(result.recommendations())
        );
    }

    private void validateUsage(UsageSummaryResponse usage) {
        requireNonNegative(usage.totalEnergyWh(), "totalEnergyWh");
        requireNonNegative(usage.averagePowerW(), "averagePowerW");
        requireNonNegative(usage.maxPowerW(), "maxPowerW");
        Objects.requireNonNull(usage.from(), "usage.from must not be null");
        Objects.requireNonNull(usage.to(), "usage.to must not be null");

        if (!usage.from().isBefore(usage.to())) {
            throw new IllegalArgumentException(
                    "usage.from must be before usage.to"
            );
        }
    }

    private void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " must be non-negative"
            );
        }
    }

    private List<String> normalizeItems(List<String> items) {
        if (items == null) {
            return List.of();
        }

        return items.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .limit(MAX_ITEMS)
                .toList();
    }
}
