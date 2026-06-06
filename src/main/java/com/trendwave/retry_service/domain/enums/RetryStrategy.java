package com.trendwave.retry_service.domain.enums;

public enum RetryStrategy {
    DO_NOT_RETRY,            // Hard decline — stop here
    RETRY_IMMEDIATE,         // Retry right away (< 2s)
    RETRY_DELAYED,           // Retry after a specific delay
    RETRY_ALTERNATE_PROCESSOR // Route to a different processor
}