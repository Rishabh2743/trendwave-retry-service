package com.trendwave.retry_service.data;

import com.trendwave.retry_service.domain.entity.RetryAttemptEntity;
import com.trendwave.retry_service.domain.entity.TransactionEntity;
import com.trendwave.retry_service.domain.enums.*;
import com.trendwave.retry_service.repository.RetryAttemptRepository;
import com.trendwave.retry_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Generates realistic synthetic transaction data that mirrors TrendWave's
 * payment patterns across MXN, COP, and CLP markets.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TestDataGenerator {

    private static final String[] PROCESSORS = {"PROC_A", "PROC_B", "PROC_C"};
    private static final double[] PROCESSOR_WEIGHTS = {0.45, 0.35, 0.20};

    // Failure code distribution: ~40% hard, ~50% soft, ~10% ambiguous
    private static final FailureCode[] HARD_CODES = {
        FailureCode.INSUFFICIENT_FUNDS, FailureCode.CARD_EXPIRED,
        FailureCode.CARD_BLOCKED, FailureCode.INVALID_CVV,
        FailureCode.DO_NOT_HONOR, FailureCode.CARD_NOT_ACTIVATED
    };
    private static final FailureCode[] SOFT_CODES = {
        FailureCode.ISSUER_TIMEOUT, FailureCode.RATE_LIMIT,
        FailureCode.VELOCITY_CHECK, FailureCode.TEMPORARY_PROCESSOR_ERROR,
        FailureCode.TRY_AGAIN, FailureCode.NETWORK_ERROR,
        FailureCode.ISSUER_UNAVAILABLE, FailureCode.RISK_SCORE_HIGH
    };
    private static final FailureCode[] AMBIGUOUS_CODES = {
        FailureCode.UNKNOWN_ERROR, FailureCode.GENERIC_DECLINE
    };

    private final TransactionRepository transactionRepository;
    private final RetryAttemptRepository retryAttemptRepository;
    private final Random random = new Random(42L); // fixed seed for reproducibility

    @Transactional
    public void generate(int count) {
        log.info("Generating {} synthetic transactions", count);
        transactionRepository.deleteAll();
        retryAttemptRepository.deleteAll();

        Instant base = Instant.now().minus(72, ChronoUnit.HOURS);

        for (int i = 0; i < count; i++) {
            Instant createdAt = base.plus(
                    (long) (random.nextDouble() * 72 * 60), ChronoUnit.MINUTES);

            boolean authorized = random.nextDouble() < 0.725; // 72.5% baseline auth rate
            String txId = "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String processor = weightedChoice(PROCESSORS, PROCESSOR_WEIGHTS);
            PaymentCurrency currency = PaymentCurrency.values()[random.nextInt(PaymentCurrency.values().length)];
            PaymentMethodType pmType = PaymentMethodType.values()[random.nextInt(PaymentMethodType.values().length)];
            BigDecimal amount = randomAmount(currency);
            String cardHash = sha256("card_" + (i % 300)); // 300 unique cards
            FailureCode failureCode = authorized ? FailureCode.TRY_AGAIN : pickFailureCode();
            DeclineType declineType = failureCode.getDeclineType();

            TransactionEntity tx = TransactionEntity.builder()
                    .id(txId)
                    .amount(amount)
                    .currency(currency)
                    .paymentMethodType(pmType)
                    .processorId(processor)
                    .failureCode(authorized ? FailureCode.TRY_AGAIN : failureCode)
                    .declineType(authorized ? DeclineType.SOFT : declineType)
                    .cardHash(cardHash)
                    .authorized(authorized)
                    .retryCount(0)
                    .build();
            transactionRepository.save(tx);

            // For soft/ambiguous failed transactions, simulate 1–2 retry attempts
            if (!authorized && declineType != DeclineType.HARD) {
                int retries = 1 + random.nextInt(2);
                boolean retrySucceeded = false;
                for (int r = 1; r <= retries && !retrySucceeded; r++) {
                    double successProb = failureCode.getBaseRecoveryRate()
                            - (r - 1) * 0.12
                            - processorRetryPenalty(processor);
                    retrySucceeded = random.nextDouble() < Math.max(0, successProb);

                    RetryAttemptEntity attempt = RetryAttemptEntity.builder()
                            .id(UUID.randomUUID().toString())
                            .transaction(tx)
                            .strategy(RetryStrategy.RETRY_DELAYED)
                            .attemptNumber(r)
                            .delaySeconds(failureCode.getDefaultRetryDelaySeconds())
                            .succeeded(retrySucceeded)
                            .confidenceScore(Math.max(0, successProb))
                            .reasoning("Simulated retry " + r + " for " + failureCode.name())
                            .build();
                    retryAttemptRepository.save(attempt);

                    if (retrySucceeded) {
                        tx.setAuthorized(true);
                        tx.setRetryCount(r);
                        transactionRepository.save(tx);
                    }
                }
            }
        }
        log.info("Test data generation complete: {} transactions", count);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private FailureCode pickFailureCode() {
        double r = random.nextDouble();
        if (r < 0.40) return HARD_CODES[random.nextInt(HARD_CODES.length)];
        if (r < 0.90) return SOFT_CODES[random.nextInt(SOFT_CODES.length)];
        return AMBIGUOUS_CODES[random.nextInt(AMBIGUOUS_CODES.length)];
    }

    private BigDecimal randomAmount(PaymentCurrency currency) {
        double base = 50 + random.nextDouble() * 4950;
        double multiplier = switch (currency) {
            case MXN -> 1.0;
            case COP -> 4000.0;
            case CLP -> 900.0;
        };
        return BigDecimal.valueOf(base * multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private String weightedChoice(String[] options, double[] weights) {
        double r = random.nextDouble();
        double cumulative = 0;
        for (int i = 0; i < options.length; i++) {
            cumulative += weights[i];
            if (r < cumulative) return options[i];
        }
        return options[options.length - 1];
    }

    private double processorRetryPenalty(String processor) {
        return switch (processor) {
            case "PROC_A" -> 0.05;
            case "PROC_B" -> 0.20;
            case "PROC_C" -> 0.10;
            default       -> 0.10;
        };
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}