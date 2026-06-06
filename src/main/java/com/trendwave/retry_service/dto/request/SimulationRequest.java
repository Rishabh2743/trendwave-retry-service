package com.trendwave.retry_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Parameters for running a simulation on stored test data")
public class SimulationRequest {

    @Schema(description = "Number of transactions to simulate (0 = all)", example = "0")
    private int limit;

    @Schema(description = "Whether to regenerate test data before simulation", example = "false")
    private boolean regenerateData;

    @Schema(description = "Number of transactions to generate if regenerateData=true", example = "1200")
    private int generateCount;
}