package com.trendwave.retry_service.service;

import com.trendwave.retry_service.dto.request.SimulationRequest;
import com.trendwave.retry_service.dto.response.SimulationResultResponse;

public interface SimulationService {
    SimulationResultResponse runSimulation(SimulationRequest request);
}