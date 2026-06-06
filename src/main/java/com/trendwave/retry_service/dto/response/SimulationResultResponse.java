package com.trendwave.retry_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@Schema(description = "Full simulation results comparing naive vs intelligent retry")
public class SimulationResultResponse {

    @Schema(description = "Total transactions in the dataset")
    private int totalTransactions;

    @Schema(description = "Authorization rate without any retries (0.0–1.0)")
    private double baselineAuthRate;

    @Schema(description = "Authorization rate with intelligent retry logic applied (0.0–1.0)")
    private double intelligentAuthRate;

    @Schema(description = "Absolute improvement in authorization rate")
    private double authRateImprovement;

    @Schema(description = "Total retries attempted")
    private int totalRetries;

    @Schema(description = "Retries that resulted in authorization")
    private int successfulRetries;

    @Schema(description = "Retry efficiency: successful / total retries")
    private double retryEfficiency;

    @Schema(description = "False retry rate: retries on hard declines / total retries")
    private double falseRetryRate;

    @Schema(description = "Average seconds from first attempt to final authorization")
    private double avgTimeToAuthSeconds;

    @Schema(description = "Transactions recovered (additional authorizations from retries)")
    private int recoveredTransactions;

    @Schema(description = "Breakdown of retry outcomes by failure code")
    private Map<String, FailureCodeStats> statsByFailureCode;

    @Data
    @Builder
    public static class FailureCodeStats {
        private int total;
        private int retried;
        private int recovered;
        private double recoveryRate;
    }
}