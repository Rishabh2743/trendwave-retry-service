package com.trendwave.retry_service.engine.impl;

import com.trendwave.retry_service.domain.enums.FailureCode;
import com.trendwave.retry_service.domain.model.ProcessorProfile;
import com.trendwave.retry_service.engine.ConfidenceScoreCalculator;
import org.springframework.stereotype.Component;

@Component
public class ConfidenceScoreCalculatorImpl implements ConfidenceScoreCalculator {

    private static final double RETRY_DECAY_PER_ATTEMPT = 0.15;
    private static final double VELOCITY_PENALTY_PER_EXTRA_ATTEMPT = 0.05;
    private static final double VELOCITY_THRESHOLD = 3;

    @Override
    public double calculate(FailureCode failureCode, ProcessorProfile profile,
                            int previousRetryCount, long recentCardAttempts) {

        // Start with the processor-observed recovery rate if available
        double base = resolveBaseRate(failureCode, profile);

        // Decay confidence with each retry — diminishing returns
        double decayed = base - (previousRetryCount * RETRY_DECAY_PER_ATTEMPT);

        // Penalise velocity: if the card has had many recent attempts, risk increases
        double velocityPenalty = Math.max(0, recentCardAttempts - VELOCITY_THRESHOLD)
                                 * VELOCITY_PENALTY_PER_EXTRA_ATTEMPT;

        double score = decayed - velocityPenalty;
        return Math.max(0.0, Math.min(1.0, score));
    }

    private double resolveBaseRate(FailureCode failureCode, ProcessorProfile profile) {
        if (profile != null) {
            return profile.getRecoveryRateByFailureCode()
                    .getOrDefault(failureCode, failureCode.getBaseRecoveryRate());
        }
        return failureCode.getBaseRecoveryRate();
    }
}