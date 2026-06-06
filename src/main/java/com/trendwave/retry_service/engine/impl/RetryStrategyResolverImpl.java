package com.trendwave.retry_service.engine.impl;

import com.trendwave.retry_service.config.AppConfig;
import com.trendwave.retry_service.domain.enums.DeclineType;
import com.trendwave.retry_service.domain.enums.FailureCode;
import com.trendwave.retry_service.domain.enums.RetryStrategy;
import com.trendwave.retry_service.domain.model.ProcessorProfile;
import com.trendwave.retry_service.engine.RetryStrategyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RetryStrategyResolverImpl implements RetryStrategyResolver {

    private final AppConfig appConfig;

    @Override
    public RetryStrategy resolve(FailureCode failureCode, ProcessorProfile profile, int previousRetryCount) {
        // Hard declines are never retried
        if (failureCode.getDeclineType() == DeclineType.HARD) {
            return RetryStrategy.DO_NOT_RETRY;
        }

        int maxAttempts = profile != null ? profile.getMaxRetryAttempts() : appConfig.getMaxAttempts();
        if (previousRetryCount >= maxAttempts) {
            return RetryStrategy.DO_NOT_RETRY;
        }

        // If the primary processor has a low recovery rate, route to alternate
        if (profile != null && profile.getPreferredAlternateProcessorId() != null) {
            double recoveryRate = profile.getRecoveryRateByFailureCode()
                    .getOrDefault(failureCode, failureCode.getBaseRecoveryRate());
            if (recoveryRate < 0.25 && previousRetryCount >= 1) {
                return RetryStrategy.RETRY_ALTERNATE_PROCESSOR;
            }
        }

        int delay = resolveDelaySeconds(failureCode, profile);
        return delay <= 2 ? RetryStrategy.RETRY_IMMEDIATE : RetryStrategy.RETRY_DELAYED;
    }

    @Override
    public int resolveDelaySeconds(FailureCode failureCode, ProcessorProfile profile) {
        // Processor-specific delay takes priority
        if (profile != null) {
            Integer profileDelay = profile.getRetryDelayByFailureCode().get(failureCode);
            if (profileDelay != null) {
                return profileDelay;
            }
        }
        // Fall back to failure-code default
        return failureCode.getDefaultRetryDelaySeconds();
    }
}