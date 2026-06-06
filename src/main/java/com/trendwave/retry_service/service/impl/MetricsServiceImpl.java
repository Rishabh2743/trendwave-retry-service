package com.trendwave.retry_service.service.impl;

import com.trendwave.retry_service.domain.enums.DeclineType;
import com.trendwave.retry_service.dto.response.MetricsSummaryResponse;
import com.trendwave.retry_service.repository.RetryAttemptRepository;
import com.trendwave.retry_service.repository.TransactionRepository;
import com.trendwave.retry_service.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MetricsServiceImpl implements MetricsService {

    private final TransactionRepository transactionRepository;
    private final RetryAttemptRepository retryAttemptRepository;

    @Override
    public MetricsSummaryResponse getSummary() {
        long total       = transactionRepository.count();
        long authorized  = transactionRepository.countByAuthorized(true);
        long totalRetries    = retryAttemptRepository.count();
        long successRetries  = retryAttemptRepository.countBySucceeded(true);
        long hardDeclines    = transactionRepository.countByDeclineType(DeclineType.HARD);
        long softDeclines    = transactionRepository.countByDeclineType(DeclineType.SOFT);
        long ambig           = transactionRepository.countByDeclineType(DeclineType.AMBIGUOUS);

        // Per-processor stats
        Map<String, MetricsSummaryResponse.ProcessorStats> perProcessor = new LinkedHashMap<>();
        transactionRepository.findAll().stream()
                .collect(Collectors.groupingBy(t -> t.getProcessorId()))
                .forEach((pid, txList) -> {
                    long attempts   = txList.size();
                    long auth       = txList.stream().filter(t -> t.isAuthorized()).count();
                    var retries     = retryAttemptRepository.findByProcessorId(pid);
                    long rTotal     = retries.size();
                    long rSuccess   = retries.stream().filter(r -> r.isSucceeded()).count();

                    perProcessor.put(pid, MetricsSummaryResponse.ProcessorStats.builder()
                            .processorId(pid)
                            .attempts(attempts)
                            .authorized(auth)
                            .authRate(attempts > 0 ? round((double) auth / attempts) : 0)
                            .retries(rTotal)
                            .retriesSucceeded(rSuccess)
                            .retryRecoveryRate(rTotal > 0 ? round((double) rSuccess / rTotal) : 0)
                            .build());
                });

        return MetricsSummaryResponse.builder()
                .totalTransactions(total)
                .totalAuthorized(authorized)
                .overallAuthRate(total > 0 ? round((double) authorized / total) : 0)
                .totalRetries(totalRetries)
                .successfulRetries(successRetries)
                .retrySuccessRate(totalRetries > 0 ? round((double) successRetries / totalRetries) : 0)
                .hardDeclines(hardDeclines)
                .softDeclines(softDeclines)
                .ambiguousDeclines(ambig)
                .perProcessorStats(perProcessor)
                .build();
    }

    @Override
    public MetricsSummaryResponse.ProcessorStats getProcessorStats(String processorId) {
        var txList  = transactionRepository.findByProcessorId(processorId);
        var retries = retryAttemptRepository.findByProcessorId(processorId);

        long attempts = txList.size();
        long auth     = txList.stream().filter(t -> t.isAuthorized()).count();
        long rTotal   = retries.size();
        long rSuccess = retries.stream().filter(r -> r.isSucceeded()).count();

        return MetricsSummaryResponse.ProcessorStats.builder()
                .processorId(processorId)
                .attempts(attempts)
                .authorized(auth)
                .authRate(attempts > 0 ? round((double) auth / attempts) : 0)
                .retries(rTotal)
                .retriesSucceeded(rSuccess)
                .retryRecoveryRate(rTotal > 0 ? round((double) rSuccess / rTotal) : 0)
                .build();
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}