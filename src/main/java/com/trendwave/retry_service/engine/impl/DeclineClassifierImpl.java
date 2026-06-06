package com.trendwave.retry_service.engine.impl;

import com.trendwave.retry_service.domain.enums.DeclineType;
import com.trendwave.retry_service.domain.enums.FailureCode;
import com.trendwave.retry_service.engine.DeclineClassifier;
import org.springframework.stereotype.Component;

@Component
public class DeclineClassifierImpl implements DeclineClassifier {

    @Override
    public DeclineType classify(FailureCode failureCode) {
        return failureCode.getDeclineType();
    }

    @Override
    public boolean isRetryable(FailureCode failureCode) {
        return switch (failureCode.getDeclineType()) {
            case HARD      -> false;
            case SOFT      -> true;
            case AMBIGUOUS -> failureCode.getBaseRecoveryRate() > 0.20;
        };
    }
}