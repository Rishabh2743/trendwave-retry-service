package com.trendwave.retry_service.engine;

import com.trendwave.retry_service.domain.enums.FailureCode;
import com.trendwave.retry_service.domain.enums.RetryStrategy;
import com.trendwave.retry_service.domain.model.ProcessorProfile;

/**
 * Resolves which retry strategy and delay to use given the failure context.
 * OCP: open for extension via new processor profiles without changing classifier.
 */
public interface RetryStrategyResolver {
    RetryStrategy resolve(FailureCode failureCode, ProcessorProfile profile, int previousRetryCount);
    int resolveDelaySeconds(FailureCode failureCode, ProcessorProfile profile);
}