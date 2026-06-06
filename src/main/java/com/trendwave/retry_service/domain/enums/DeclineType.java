package com.trendwave.retry_service.domain.enums;

public enum DeclineType {
    HARD,       // Never retry — permanent failure
    SOFT,       // Retry with appropriate delay
    AMBIGUOUS   // Unknown — retry once with caution
}