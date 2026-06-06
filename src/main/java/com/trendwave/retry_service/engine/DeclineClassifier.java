package com.trendwave.retry_service.engine;

import com.trendwave.retry_service.domain.enums.DeclineType;
import com.trendwave.retry_service.domain.enums.FailureCode;

/**
 * Classifies a failure code as HARD, SOFT, or AMBIGUOUS.
 * SRP: single responsibility — classification only.
 */
public interface DeclineClassifier {
    DeclineType classify(FailureCode failureCode);
    boolean isRetryable(FailureCode failureCode);
}