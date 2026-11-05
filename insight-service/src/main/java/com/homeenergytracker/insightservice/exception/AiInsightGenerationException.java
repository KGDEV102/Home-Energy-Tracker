package com.homeenergytracker.insightservice.exception;

import com.homeenergytracker.common.exception.BusinessException;

public class AiInsightGenerationException extends BusinessException {

    private static final String CODE = "AI_INSIGHT_GENERATION_FAILED";

    public AiInsightGenerationException(String message) {
        super(CODE, message);
    }

    public AiInsightGenerationException(String message, Throwable cause) {
        super(CODE, message, cause);
    }
}
