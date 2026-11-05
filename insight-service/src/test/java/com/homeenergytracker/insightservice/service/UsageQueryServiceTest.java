package com.homeenergytracker.insightservice.service;

import com.homeenergytracker.common.response.SuccessResponse;
import com.homeenergytracker.insightservice.client.UsageServiceClient;
import com.homeenergytracker.insightservice.dto.UsageSummaryResponse;
import com.homeenergytracker.insightservice.exception.UsageServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageQueryServiceTest {

    private static final Long USER_ID = 42L;
    private static final String INTERNAL_TOKEN = "insight-to-usage-token";
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");

    @Mock
    private UsageServiceClient usageServiceClient;

    private UsageQueryService usageQueryService;

    @BeforeEach
    void setUp() {
        usageQueryService = new UsageQueryService(
                usageServiceClient,
                INTERNAL_TOKEN
        );
    }

    @Test
    void getSummary_shouldReturnDataFromUsageService() {
        UsageSummaryResponse summary = new UsageSummaryResponse(
                new BigDecimal("12500.50"),
                new BigDecimal("520.25"),
                new BigDecimal("1800.00"),
                FROM,
                TO
        );

        when(usageServiceClient.getSummary(USER_ID, FROM, TO, INTERNAL_TOKEN))
                .thenReturn(SuccessResponse.of(summary));

        UsageSummaryResponse result = usageQueryService.getSummary(
                USER_ID,
                FROM,
                TO
        );

        assertSame(summary, result);
        verify(usageServiceClient).getSummary(
                USER_ID,
                FROM,
                TO,
                INTERNAL_TOKEN
        );
    }

    @Test
    void constructor_shouldRejectBlankInternalToken() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new UsageQueryService(usageServiceClient, "  ")
        );

        assertEquals(
                "internal.usage-service-token must not be blank",
                exception.getMessage()
        );
    }

    @Test
    void getSummary_shouldRejectMissingUserIdBeforeCallingClient() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> usageQueryService.getSummary(null, FROM, TO)
        );

        assertEquals("userId must be positive", exception.getMessage());
        verify(usageServiceClient, never()).getSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void getSummary_shouldRejectMissingFromBeforeCallingClient() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> usageQueryService.getSummary(USER_ID, null, TO)
        );

        assertEquals("from must not be null", exception.getMessage());
        verify(usageServiceClient, never()).getSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void getSummary_shouldRejectMissingToBeforeCallingClient() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> usageQueryService.getSummary(USER_ID, FROM, null)
        );

        assertEquals("to must not be null", exception.getMessage());
        verify(usageServiceClient, never()).getSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void getSummary_shouldRejectEqualTimeRangeBeforeCallingClient() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> usageQueryService.getSummary(USER_ID, FROM, FROM)
        );

        assertEquals("from must be before to", exception.getMessage());
        verify(usageServiceClient, never()).getSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void getSummary_shouldRejectNullWrapper() {
        when(usageServiceClient.getSummary(USER_ID, FROM, TO, INTERNAL_TOKEN))
                .thenReturn(null);

        assertInvalidUsageResponse();
    }

    @Test
    void getSummary_shouldRejectUnsuccessfulWrapper() {
        when(usageServiceClient.getSummary(USER_ID, FROM, TO, INTERNAL_TOKEN))
                .thenReturn(new SuccessResponse<>(false, null, "Usage service failed"));

        assertInvalidUsageResponse();
    }

    @Test
    void getSummary_shouldRejectWrapperWithoutData() {
        when(usageServiceClient.getSummary(USER_ID, FROM, TO, INTERNAL_TOKEN))
                .thenReturn(SuccessResponse.of());

        assertInvalidUsageResponse();
    }

    @Test
    void getSummary_shouldWrapClientFailure() {
        IllegalStateException cause = new IllegalStateException(
                "usage-service unavailable"
        );
        when(usageServiceClient.getSummary(USER_ID, FROM, TO, INTERNAL_TOKEN))
                .thenThrow(cause);

        UsageServiceUnavailableException exception = assertThrows(
                UsageServiceUnavailableException.class,
                () -> usageQueryService.getSummary(USER_ID, FROM, TO)
        );

        assertEquals(
                "Could not load usage summary from usage-service",
                exception.getMessage()
        );
        assertSame(cause, exception.getCause());
    }

    private void assertInvalidUsageResponse() {
        UsageServiceUnavailableException exception = assertThrows(
                UsageServiceUnavailableException.class,
                () -> usageQueryService.getSummary(USER_ID, FROM, TO)
        );

        assertEquals(
                "Could not load usage summary from usage-service",
                exception.getMessage()
        );
    }
}
