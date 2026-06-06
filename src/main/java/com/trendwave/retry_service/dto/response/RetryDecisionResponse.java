package com.trendwave.retry_service.dto.response;

import com.trendwave.retry_service.domain.enums.DeclineType;
import com.trendwave.retry_service.domain.enums.RetryStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Retry recommendation for a failed authorization")
public class RetryDecisionResponse {

    @Schema(description = "Original transaction ID", example = "txn_abc123")
    private String transactionId;

    @Schema(description = "Whether a retry is recommended", example = "true")
    private boolean shouldRetry;

    @Schema(description = "Recommended retry strategy")
    private RetryStrategy retryStrategy;

    @Schema(description = "Seconds to wait before retrying (0 = immediate)", example = "10")
    private int retryDelaySeconds;

    @Schema(description = "Alternate processor ID if strategy is RETRY_ALTERNATE_PROCESSOR", example = "PROC_B")
    private String alternateProcessorId;

    @Schema(description = "Confidence score that retry will succeed (0.0–1.0)", example = "0.68")
    private double confidenceScore;

    @Schema(description = "Classification of the decline")
    private DeclineType declineType;

    @Schema(description = "Human-readable reasoning for the decision",
            example = "Soft decline due to issuer timeout — historical retry success rate is 68% after 10-second delay")
    private String reasoning;

    @Schema(description = "Maximum recommended total retry attempts", example = "3")
    private int maxRetryAttempts;
}