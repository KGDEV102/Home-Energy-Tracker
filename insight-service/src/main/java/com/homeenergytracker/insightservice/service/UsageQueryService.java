package com.homeenergytracker.insightservice.service;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.insightservice.client.UsageServiceClient;
import com.homeenergytracker.insightservice.dto.UsageSummaryResponse;
import com.homeenergytracker.insightservice.exception.UsageServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

@Service
public class UsageQueryService {

    private final UsageServiceClient usageServiceClient;
    private final String internalToken;

    public UsageQueryService(
            UsageServiceClient usageServiceClient,
            @Value("${internal.usage-service-token}") String internalToken
    ) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalArgumentException(
                    "internal.usage-service-token must not be blank"
            );
        }
        this.usageServiceClient = usageServiceClient;
        this.internalToken = internalToken;
    }

    @CircuitBreaker(
            name = "usageService",
            fallbackMethod = "usageServiceFallback"
    )
    public UsageSummaryResponse getSummary(
            Long userId,
            Instant from,
            Instant to
    ) {
        validateRequest(userId, from, to);

        try {
            SuccessResponse<UsageSummaryResponse> response =
                    usageServiceClient.getSummary(
                            userId,
                            from,
                            to,
                            internalToken
                    );

            if (response == null
                    || !response.success()
                    || response.data() == null) {
                throw new UsageServiceUnavailableException(
                        "Could not load usage summary from usage-service"
                );
            }

            return response.data();
        } catch (UsageServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new UsageServiceUnavailableException(
                    "Could not load usage summary from usage-service",
                    exception
            );
        }
    }

    private UsageSummaryResponse usageServiceFallback(
            Long userId,
            Instant from,
            Instant to,
            Throwable cause
    ) {
        if (cause instanceof IllegalArgumentException exception) {
            throw exception;
        }
        if (cause instanceof NullPointerException exception) {
            throw exception;
        }

        throw new UsageServiceUnavailableException(
                "Usage service is temporarily unavailable",
                cause
        );
    }

    private void validateRequest(
            Long userId,
            Instant from,
            Instant to
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "userId must be positive"
            );
        }

        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");

        if (!from.isBefore(to)) {
            throw new IllegalArgumentException(
                    "from must be before to"
            );
        }
    }
}
