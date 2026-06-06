package com.trendwave.retry_service.service.impl;

import com.trendwave.retry_service.data.TestDataGenerator;
import com.trendwave.retry_service.domain.entity.TransactionEntity;
import com.trendwave.retry_service.domain.enums.DeclineType;
import com.trendwave.retry_service.domain.enums.FailureCode;
import com.trendwave.retry_service.domain.model.ProcessorProfile;
import com.trendwave.retry_service.dto.request.SimulationRequest;
import com.trendwave.retry_service.dto.response.SimulationResultResponse;
import com.trendwave.retry_service.engine.ConfidenceScoreCalculator;
import com.trendwave.retry_service.engine.DeclineClassifier;
import com.trendwave.retry_service.engine.RetryStrategyResolver;
import com.trendwave.retry_service.repository.TransactionRepository;
import com.trendwave.retry_service.service.ProcessorProfileService;
import com.trendwave.retry_service.service.SimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SimulationServiceImpl implements SimulationService {

    private final TransactionRepository transactionRepository;
    private final DeclineClassifier declineClassifier;
    private final RetryStrategyResolver retryStrategyResolver;
    private final ConfidenceScoreCalculator confidenceScoreCalculator;
    private final ProcessorProfileService processorProfileService;
    private final TestDataGenerator testDataGenerator;

    @Override
    public SimulationResultResponse runSimulation(SimulationRequest request) {
        if (request.isRegenerateData()) {
            int count = request.getGenerateCount() > 0 ? request.getGenerateCount() : 1200;
            log.info("Generating {} test transactions", count);
            testDataGenerator.generate(count);
        }

        List<TransactionEntity> transactions = request.getLimit() > 0
                ? transactionRepository.findAll().stream().limit(request.getLimit()).toList()
                : transactionRepository.findAll();

        log.info("Running simulation on {} transactions", transactions.size());

        // ── Baseline: count only initially-authorized transactions ──────────
        long baselineAuthorized = transactions.stream().filter(TransactionEntity::isAuthorized).count();
        double baselineRate = (double) baselineAuthorized / transactions.size();

        // ── Apply intelligent retry logic ────────────────────────────────────
        int totalRetries          = 0;
        int successfulRetries     = 0;
        int falseRetries          = 0;
        long totalRecoveryTimeMs  = 0;
        int recoveredCount        = 0;

        Map<FailureCode, int[]> statsMap = new EnumMap<>(FailureCode.class);
        // int[0]=total, int[1]=retried, int[2]=recovered

        for (TransactionEntity tx : transactions) {
            if (tx.isAuthorized()) continue; // already authorized — no retry needed

            FailureCode fc = tx.getFailureCode();
            statsMap.computeIfAbsent(fc, k -> new int[3]);
            statsMap.get(fc)[0]++;

            if (!declineClassifier.isRetryable(fc)) {
                falseRetries++; // would be a wasted retry in naive system
                continue;
            }

            ProcessorProfile profile = processorProfileService.getProfile(tx.getProcessorId());
            double confidence = confidenceScoreCalculator.calculate(fc, profile, tx.getRetryCount(), 0);
            int delaySecs = retryStrategyResolver.resolveDelaySeconds(fc, profile);

            totalRetries++;
            statsMap.get(fc)[1]++;

            // Simulate success/failure based on confidence score
            boolean recovered = (Math.random() < confidence);
            if (recovered) {
                successfulRetries++;
                recoveredCount++;
                totalRecoveryTimeMs += (delaySecs * 1000L);
                statsMap.get(fc)[2]++;
            }
        }

        double intelligentRate = (baselineAuthorized + successfulRetries) / (double) transactions.size();
        double retryEfficiency = totalRetries > 0 ? (double) successfulRetries / totalRetries : 0.0;
        // False retry = hard decline that was retried.
// Since hard declines should never be retried, this should be 0.

long totalHardDeclines = transactions.stream()
        .filter(t -> !t.isAuthorized())
        .filter(t -> t.getFailureCode() == FailureCode.INSUFFICIENT_FUNDS
                || t.getFailureCode() == FailureCode.CARD_EXPIRED
                || t.getFailureCode() == FailureCode.CARD_BLOCKED
                || t.getFailureCode() == FailureCode.INVALID_CVV
                || t.getFailureCode() == FailureCode.CARD_NOT_ACTIVATED
                || t.getFailureCode() == FailureCode.DO_NOT_HONOR)
        .count();

long hardDeclinesRetried = 0L;

// If in future you track retry count:
/// hardDeclinesRetried = transactions.stream()
///         .filter(...)
///         .filter(t -> t.getRetryCount() > 0)
///         .count();

double falseRetryRate = totalHardDeclines == 0
        ? 0.0
        : (double) hardDeclinesRetried / totalHardDeclines;
        double avgTimeToAuth = recoveredCount > 0
                ? (totalRecoveryTimeMs / 1000.0) / recoveredCount : 0.0;

        // ── Build per-failure-code breakdown ─────────────────────────────────
        Map<String, SimulationResultResponse.FailureCodeStats> breakdown = new LinkedHashMap<>();
        statsMap.forEach((fc, arr) -> {
            int total = arr[0], retried = arr[1], recovered = arr[2];
            breakdown.put(fc.name(), SimulationResultResponse.FailureCodeStats.builder()
                    .total(total)
                    .retried(retried)
                    .recovered(recovered)
                    .recoveryRate(retried > 0 ? (double) recovered / retried : 0.0)
                    .build());
        });

        return SimulationResultResponse.builder()
                .totalTransactions(transactions.size())
                .baselineAuthRate(round(baselineRate))
                .intelligentAuthRate(round(intelligentRate))
                .authRateImprovement(round(intelligentRate - baselineRate))
                .totalRetries(totalRetries)
                .successfulRetries(successfulRetries)
                .retryEfficiency(round(retryEfficiency))
                .falseRetryRate(round(falseRetryRate))
                .avgTimeToAuthSeconds(round(avgTimeToAuth))
                .recoveredTransactions(recoveredCount)
                .statsByFailureCode(breakdown)
                .build();
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}