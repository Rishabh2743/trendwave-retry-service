package com.trendwave.retry_service.controller;

import com.trendwave.retry_service.dto.request.AuthorizationFailureRequest;
import com.trendwave.retry_service.dto.response.RetryDecisionResponse;
import com.trendwave.retry_service.service.RetryDecisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/retry")
@RequiredArgsConstructor
@Tag(name = "Retry Decision", description = "Intelligent payment authorization retry engine")
public class RetryDecisionController {

    private final RetryDecisionService retryDecisionService;

    @PostMapping("/decision")
    @Operation(
        summary = "Get retry recommendation for a failed authorization",
        description = "Analyzes the failure reason, processor behavior, and card velocity to return an actionable retry strategy"
    )
    public ResponseEntity<RetryDecisionResponse> decide(
            @Valid @RequestBody AuthorizationFailureRequest request) {
        return ResponseEntity.ok(retryDecisionService.decide(request));
    }

    @GetMapping("/decision/{transactionId}")
    @Operation(
        summary = "Get retry decision history for a transaction",
        description = "Returns all retry attempts and decisions made for a given transaction"
    )
    public ResponseEntity<List<RetryDecisionResponse>> getHistory(
            @PathVariable String transactionId) {
        return ResponseEntity.ok(retryDecisionService.getHistory(transactionId));
    }
}