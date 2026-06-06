package com.trendwave.retry_service.domain.model;

import com.trendwave.retry_service.domain.entity.TransactionEntity;
import com.trendwave.retry_service.domain.enums.FailureCode;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * In-memory model for processor-specific retry behaviour.
 * Populated from config + continuously updated from historical data.
 */
@Data
@Builder
public class ProcessorProfile {

    private String processorId;

    /** Base retry delay per failure code, in seconds */
    private Map<FailureCode, Integer> retryDelayByFailureCode;

    /** Observed recovery rate per failure code (0.0 – 1.0) */
    private Map<FailureCode, Double> recoveryRateByFailureCode;

    /** Max allowed retry attempts before giving up */
    private int maxRetryAttempts;

    /** Total retries observed (for statistical confidence) */
    private long totalRetriesObserved;

    /** Whether this processor has a known alternate to route to */
    private String preferredAlternateProcessorId;
}