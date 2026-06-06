package com.trendwave.retry_service.service.impl;

import com.trendwave.retry_service.domain.entity.RetryAttemptEntity;
import com.trendwave.retry_service.domain.entity.TransactionEntity;
import com.trendwave.retry_service.domain.enums.DeclineType;
import com.trendwave.retry_service.domain.enums.FailureCode;
import com.trendwave.retry_service.domain.enums.RetryStrategy;
import com.trendwave.retry_service.domain.model.ProcessorProfile;
import com.trendwave.retry_service.dto.request.AuthorizationFailureRequest;
import com.trendwave.retry_service.dto.response.RetryDecisionResponse;
import com.trendwave.retry_service.engine.ConfidenceScoreCalculator;
import com.trendwave.retry_service.engine.DeclineClassifier;
import com.trendwave.retry_service.engine.RetryStrategyResolver;
import com.trendwave.retry_service.repository.RetryAttemptRepository;
import com.trendwave.retry_service.repository.TransactionRepository;
import com.trendwave.retry_service.service.ProcessorProfileService;
import com.trendwave.retry_service.service.RetryDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RetryDecisionServiceImpl implements RetryDecisionService {

    private final DeclineClassifier declineClassifier;
    private final RetryStrategyResolver retryStrategyResolver;
    private final ConfidenceScoreCalculator confidenceScoreCalculator;
    private final ProcessorProfileService processorProfileService;
    private final TransactionRepository transactionRepository;
    private final RetryAttemptRepository retryAttemptRepository;

    @Override
    public RetryDecisionResponse decide(AuthorizationFailureRequest request) {
        log.debug("Processing retry decision for transaction: {}", request.getTransactionId());

        FailureCode failureCode = request.getFailureCode();
        DeclineType declineType = declineClassifier.classify(failureCode);
        ProcessorProfile profile = processorProfileService.getProfile(request.getProcessorId());

        // Velocity check: how many recent attempts from this card?
        long recentAttempts = transactionRepository.countRecentAttemptsByCard(
                request.getCardHash(),
                Instant.now().minus(5, ChronoUnit.MINUTES));

        RetryStrategy strategy = retryStrategyResolver.resolve(
                failureCode, profile, request.getPreviousRetryCount());

        int delaySeconds = retryStrategyResolver.resolveDelaySeconds(failureCode, profile);

        double confidenceScore = confidenceScoreCalculator.calculate(
                failureCode, profile, request.getPreviousRetryCount(), recentAttempts);

        String alternateProcessor = strategy == RetryStrategy.RETRY_ALTERNATE_PROCESSOR
                ? (profile != null ? profile.getPreferredAlternateProcessorId() : null)
                : null;

        String reasoning = buildReasoning(failureCode, declineType, strategy,
                confidenceScore, delaySeconds, recentAttempts);

        // Persist transaction record
        persistTransaction(request, declineType);

        // Persist retry attempt record
        persistRetryAttempt(request, strategy, delaySeconds, confidenceScore, reasoning, alternateProcessor);

        return RetryDecisionResponse.builder()
                .transactionId(request.getTransactionId())
                .shouldRetry(strategy != RetryStrategy.DO_NOT_RETRY)
                .retryStrategy(strategy)
                .retryDelaySeconds(delaySeconds)
                .alternateProcessorId(alternateProcessor)
                .confidenceScore(confidenceScore)
                .declineType(declineType)
                .reasoning(reasoning)
                .maxRetryAttempts(profile != null ? profile.getMaxRetryAttempts() : 3)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RetryDecisionResponse> getHistory(String transactionId) {
        return retryAttemptRepository.findByTransactionId(transactionId).stream()
                .map(attempt -> RetryDecisionResponse.builder()
                        .transactionId(transactionId)
                        .shouldRetry(attempt.getStrategy() != RetryStrategy.DO_NOT_RETRY)
                        .retryStrategy(attempt.getStrategy())
                        .retryDelaySeconds(attempt.getDelaySeconds())
                        .alternateProcessorId(attempt.getAlternateProcessorId())
                        .confidenceScore(attempt.getConfidenceScore())
                        .reasoning(attempt.getReasoning())
                        .build())
                .toList();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void persistTransaction(AuthorizationFailureRequest req, DeclineType declineType) {
        if (!transactionRepository.existsById(req.getTransactionId())) {
            transactionRepository.save(TransactionEntity.builder()
                    .id(req.getTransactionId())
                    .amount(req.getAmount())
                    .currency(req.getCurrency())
                    .paymentMethodType(req.getPaymentMethodType())
                    .processorId(req.getProcessorId())
                    .failureCode(req.getFailureCode())
                    .declineType(declineType)
                    .cardHash(req.getCardHash())
                    .authorized(false)
                    .retryCount(req.getPreviousRetryCount())
                    .build());
        }
    }

    private void persistRetryAttempt(AuthorizationFailureRequest req, RetryStrategy strategy,
                                     int delaySeconds, double confidenceScore,
                                     String reasoning, String alternateProcessor) {
        transactionRepository.findById(req.getTransactionId()).ifPresent(tx -> {
            RetryAttemptEntity attempt = RetryAttemptEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .transaction(tx)
                    .strategy(strategy)
                    .attemptNumber(req.getPreviousRetryCount() + 1)
                    .delaySeconds(delaySeconds)
                    .succeeded(false) // will be updated externally when outcome known
                    .confidenceScore(confidenceScore)
                    .reasoning(reasoning)
                    .alternateProcessorId(alternateProcessor)
                    .build();
            retryAttemptRepository.save(attempt);
        });
    }

    private String buildReasoning(FailureCode code, DeclineType type,
                                   RetryStrategy strategy, double confidence,
                                   int delay, long recentAttempts) {
        return switch (strategy) {
            case DO_NOT_RETRY -> String.format(
                    "%s decline (%s) — %s. Retry will not succeed. No action recommended.",
                    type, code.name(), code.getDescription());
            case RETRY_IMMEDIATE -> String.format(
                    "Soft decline (%s) — %s. Retrying immediately. Confidence: %.0f%%.",
                    code.name(), code.getDescription(), confidence * 100);
            case RETRY_DELAYED -> String.format(
                    "Soft decline (%s) — %s. Retry after %ds delay. " +
                    "Historical recovery rate: %.0f%%. Recent card attempts: %d.",
                    code.name(), code.getDescription(), delay, confidence * 100, recentAttempts);
            case RETRY_ALTERNATE_PROCESSOR -> String.format(
                    "Soft decline (%s) — primary processor has low recovery rate. " +
                    "Routing to alternate processor. Confidence: %.0f%%.",
                    code.name(), confidence * 100);
        };
    }
}