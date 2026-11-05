package com.homeenergytracker.insightservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class InsightExceptionHandlerTest {

    private final InsightExceptionHandler handler = new InsightExceptionHandler();

    @Test
    void mapsAiGenerationFailureToSharedErrorResponse() {
        var exception = new AiInsightGenerationException("Groq is unavailable");
        var request = new ServletWebRequest(new MockHttpServletRequest("POST", "/api/v1/insights"));

        var response = handler.handleAiInsightGeneration(exception, request);

        assertThat(exception.getCode()).isEqualTo("AI_INSIGHT_GENERATION_FAILED");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().error().code()).isEqualTo("AI_INSIGHT_GENERATION_FAILED");
        assertThat(response.getBody().error().message()).isEqualTo("Groq is unavailable");
    }

    @Test
    void mapsUsageServiceFailureToSharedErrorResponse() {
        var exception = new UsageServiceUnavailableException(
                "Usage service is temporarily unavailable"
        );
        var request = new ServletWebRequest(new MockHttpServletRequest(
                "POST",
                "/api/v1/insights/generate"
        ));

        var response = handler.handleUsageServiceUnavailable(
                exception,
                request
        );

        assertThat(exception.getCode()).isEqualTo("USAGE_SERVICE_UNAVAILABLE");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().error().code()).isEqualTo("USAGE_SERVICE_UNAVAILABLE");
        assertThat(response.getBody().error().message())
                .isEqualTo("Usage service is temporarily unavailable");
    }
}
