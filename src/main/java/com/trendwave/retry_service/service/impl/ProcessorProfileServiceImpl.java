package com.trendwave.retry_service.service.impl;

import com.trendwave.retry_service.config.AppConfig;
import com.trendwave.retry_service.domain.enums.FailureCode;
import com.trendwave.retry_service.domain.model.ProcessorProfile;
import com.trendwave.retry_service.repository.RetryAttemptRepository;
import com.trendwave.retry_service.repository.TransactionRepository;
import com.trendwave.retry_service.service.ProcessorProfileService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessorProfileServiceImpl implements ProcessorProfileService {

    private final AppConfig appConfig;
    private final TransactionRepository transactionRepository;
    private final RetryAttemptRepository retryAttemptRepository;

    /** Thread-safe in-memory profile store. In production, back with Redis. */
    private final Map<String, ProcessorProfile> profileCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initProfiles() {
        appConfig.getProcessors().forEach((processorId, config) -> {
            Map<FailureCode, Integer> delayMap = buildDefaultDelayMap(config.getTimeoutRetryDelaySeconds());
            Map<FailureCode, Double> rateMap   = buildDefaultRateMap(config.getTimeoutRecoveryRate());

            String altProcessor = switch (processorId) {
                case "PROC_A" -> "PROC_B";
                case "PROC_B" -> "PROC_C";
                case "PROC_C" -> "PROC_A";
                default       -> null;
            };

            profileCache.put(processorId, ProcessorProfile.builder()
                    .processorId(processorId)
                    .retryDelayByFailureCode(delayMap)
                    .recoveryRateByFailureCode(rateMap)
                    .maxRetryAttempts(config.getMaxRetryAttempts())
                    .totalRetriesObserved(0L)
                    .preferredAlternateProcessorId(altProcessor)
                    .build());
        });
        log.info("Initialised {} processor profiles", profileCache.size());
    }

    @Override
    public ProcessorProfile getProfile(String processorId) {
        return profileCache.getOrDefault(processorId, buildGenericProfile(processorId));
    }

    @Override
    public void updateProfile(String processorId, ProcessorProfile profile) {
        profileCache.put(processorId, profile);
    }

    @Override
    public List<ProcessorProfile> getAllProfiles() {
        return new ArrayList<>(profileCache.values());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ProcessorProfile buildGenericProfile(String processorId) {
        return ProcessorProfile.builder()
                .processorId(processorId)
                .retryDelayByFailureCode(buildDefaultDelayMap(10))
                .recoveryRateByFailureCode(buildDefaultRateMap(0.40))
                .maxRetryAttempts(3)
                .totalRetriesObserved(0L)
                .build();
    }

    private Map<FailureCode, Integer> buildDefaultDelayMap(int timeoutDelay) {
        Map<FailureCode, Integer> map = new EnumMap<>(FailureCode.class);
        for (FailureCode fc : FailureCode.values()) {
            int delay = fc == FailureCode.ISSUER_TIMEOUT ? timeoutDelay
                      : fc == FailureCode.RATE_LIMIT     ? timeoutDelay * 3
                      : fc.getDefaultRetryDelaySeconds();
            map.put(fc, delay);
        }
        return map;
    }

    private Map<FailureCode, Double> buildDefaultRateMap(double timeoutRate) {
        Map<FailureCode, Double> map = new EnumMap<>(FailureCode.class);
        for (FailureCode fc : FailureCode.values()) {
            double rate = fc == FailureCode.ISSUER_TIMEOUT ? timeoutRate
                        : fc.getBaseRecoveryRate();
            map.put(fc, rate);
        }
        return map;
    }
}