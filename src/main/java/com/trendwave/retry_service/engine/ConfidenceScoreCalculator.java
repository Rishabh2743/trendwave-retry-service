package com.trendwave.retry_service.engine;

import com.trendwave.retry_service.domain.enums.FailureCode;
import com.trendwave.retry_service.domain.model.ProcessorProfile;

/**
 * Calculates the probability that a retry will succeed.
 */
public interface ConfidenceScoreCalculator {
    double calculate(FailureCode failureCode, ProcessorProfile profile,
                     int previousRetryCount, long recentCardAttempts);
}