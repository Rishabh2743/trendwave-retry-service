package com.trendwave.retry_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@Schema(description = "Live metrics summary from stored transaction data")
public class MetricsSummaryResponse {

    private long totalTransactions;
    private long totalAuthorized;
    private double overallAuthRate;

    private long totalRetries;
    private long successfulRetries;
    private double retrySuccessRate;

    private long hardDeclines;
    private long softDeclines;
    private long ambiguousDeclines;

    @Schema(description = "Per-processor authorization and retry stats")
    private Map<String, ProcessorStats> perProcessorStats;

    @Data
    @Builder
    public static class ProcessorStats {
        private String processorId;
        private long attempts;
        private long authorized;
        private double authRate;
        private long retries;
        private long retriesSucceeded;
        private double retryRecoveryRate;
    }
}