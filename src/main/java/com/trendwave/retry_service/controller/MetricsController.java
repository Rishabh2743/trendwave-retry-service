package com.trendwave.retry_service.controller;

import com.trendwave.retry_service.dto.response.MetricsSummaryResponse;
import com.trendwave.retry_service.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Authorization rate and retry performance metrics")
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping("/summary")
    @Operation(summary = "Get overall metrics summary",
               description = "Returns auth rates, retry success rates, decline distribution, and per-processor stats")
    public ResponseEntity<MetricsSummaryResponse> getSummary() {
        return ResponseEntity.ok(metricsService.getSummary());
    }

    @GetMapping("/processor/{processorId}")
    @Operation(summary = "Get per-processor retry stats",
               description = "Returns authorization rate and retry recovery rate for a specific processor")
    public ResponseEntity<MetricsSummaryResponse.ProcessorStats> getProcessorStats(
            @PathVariable String processorId) {
        return ResponseEntity.ok(metricsService.getProcessorStats(processorId));
    }
}