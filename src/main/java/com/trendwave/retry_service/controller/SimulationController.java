package com.trendwave.retry_service.controller;

import com.trendwave.retry_service.data.TestDataGenerator;
import com.trendwave.retry_service.dto.request.SimulationRequest;
import com.trendwave.retry_service.dto.response.SimulationResultResponse;
import com.trendwave.retry_service.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Simulation", description = "Run intelligent retry simulation against historical data")
public class SimulationController {

    private final SimulationService simulationService;
    private final TestDataGenerator testDataGenerator;

    @PostMapping("/simulation/run")
    @Operation(
        summary = "Run retry simulation",
        description = "Applies intelligent retry logic to stored transactions and returns before/after authorization rate comparison"
    )
    public ResponseEntity<SimulationResultResponse> runSimulation(
            @RequestBody(required = false) SimulationRequest request) {
        if (request == null) request = new SimulationRequest();
        return ResponseEntity.ok(simulationService.runSimulation(request));
    }

    @PostMapping("/data/generate")
    @Operation(
        summary = "Generate test dataset",
        description = "Generates realistic synthetic payment transactions for simulation"
    )
    public ResponseEntity<String> generateData(
            @RequestParam(defaultValue = "1200") int count) {
        testDataGenerator.generate(count);
        return ResponseEntity.ok("Generated " + count + " transactions successfully.");
    }
}