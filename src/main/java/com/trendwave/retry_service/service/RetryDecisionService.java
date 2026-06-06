package com.trendwave.retry_service.service;

import com.trendwave.retry_service.dto.request.AuthorizationFailureRequest;
import com.trendwave.retry_service.dto.response.RetryDecisionResponse;

import java.util.List;

public interface RetryDecisionService {
    RetryDecisionResponse decide(AuthorizationFailureRequest request);
    List<RetryDecisionResponse> getHistory(String transactionId);
}