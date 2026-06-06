package com.trendwave.retry_service.service;

import com.trendwave.retry_service.dto.response.MetricsSummaryResponse;

public interface MetricsService {
    MetricsSummaryResponse getSummary();
    MetricsSummaryResponse.ProcessorStats getProcessorStats(String processorId);
}