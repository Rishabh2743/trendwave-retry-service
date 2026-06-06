package com.trendwave.retry_service.domain.model;

import com.trendwave.retry_service.domain.enums.RetryStrategy;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class RetryAttempt {
    private String id;
    private String transactionId;
    private RetryStrategy strategy;
    private int attemptNumber;
    private int delaySeconds;
    private boolean succeeded;
    private double confidenceScore;
    private String reasoning;
    private String alternateProcessorId;
    private Instant createdAt;
}