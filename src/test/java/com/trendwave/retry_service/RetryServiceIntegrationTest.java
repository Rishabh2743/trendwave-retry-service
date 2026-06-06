package com.trendwave.retry_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendwave.retry_service.data.TestDataGenerator;
import com.trendwave.retry_service.dto.request.AuthorizationFailureRequest;
import com.trendwave.retry_service.domain.enums.FailureCode;
import com.trendwave.retry_service.domain.enums.PaymentCurrency;
import com.trendwave.retry_service.domain.enums.PaymentMethodType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("TrendWave Retry Service — Full Integration Tests")
class RetryServiceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestDataGenerator testDataGenerator;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. HEALTH CHECK
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("TC-01 | Health check returns UP")
    void tc01_healthCheck() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. TEST DATA GENERATION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("TC-02 | Generate 1200 test transactions")
    void tc02_generateTestData() throws Exception {
        mockMvc.perform(post("/api/v1/data/generate?count=1200"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1200")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. SIMULATION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("TC-03 | Simulation returns improved auth rate over baseline")
    void tc03_simulationImprovesAuthRate() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/simulation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(greaterThan(0)))
                .andExpect(jsonPath("$.baselineAuthRate").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.intelligentAuthRate").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.authRateImprovement").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.retryEfficiency").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.recoveredTransactions").value(greaterThanOrEqualTo(0)))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        System.out.println("\n📊 SIMULATION RESULT:\n" + prettyPrint(body));

        // Core assertion: intelligent retry must be >= baseline
        double baseline    = extractDouble(body, "baselineAuthRate");
        double intelligent = extractDouble(body, "intelligentAuthRate");
        assertTrue(intelligent >= baseline,
                "Intelligent auth rate (" + intelligent + ") must be >= baseline (" + baseline + ")");
    }

    @Test
    @Order(4)
    @DisplayName("TC-04 | Simulation false retry rate is 0 (hard declines never retried)")
    void tc04_noFalseRetries() throws Exception {
        mockMvc.perform(post("/api/v1/simulation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.falseRetryRate").value(0.0));
    }

    @Test
    @Order(5)
    @DisplayName("TC-05 | Simulation with regeneration produces fresh data")
    void tc05_simulationWithRegeneration() throws Exception {
        String body = """
                {
                  "regenerateData": true,
                  "generateCount": 500
                }
                """;
        mockMvc.perform(post("/api/v1/simulation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(greaterThanOrEqualTo(500)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. METRICS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("TC-06 | Metrics summary returns all required fields")
    void tc06_metricsSummary() throws Exception {
        // Re-generate clean data first
        testDataGenerator.generate(1200);

        MvcResult result = mockMvc.perform(get("/api/v1/metrics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(greaterThan(0)))
                .andExpect(jsonPath("$.overallAuthRate").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.hardDeclines").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.softDeclines").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.perProcessorStats").exists())
                .andReturn();

        System.out.println("\n📈 METRICS SUMMARY:\n" + prettyPrint(result.getResponse().getContentAsString()));
    }

    @Test
    @Order(7)
    @DisplayName("TC-07 | Per-processor stats available for PROC_A")
    void tc07_processorStatsA() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/metrics/processor/PROC_A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processorId").value("PROC_A"))
                .andExpect(jsonPath("$.attempts").value(greaterThan(0)))
                .andExpect(jsonPath("$.authRate").value(greaterThan(0.0)))
                .andReturn();

        System.out.println("\n🔧 PROC_A STATS:\n" + prettyPrint(result.getResponse().getContentAsString()));
    }

    @Test
    @Order(8)
    @DisplayName("TC-08 | Per-processor stats available for PROC_B")
    void tc08_processorStatsB() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/processor/PROC_B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processorId").value("PROC_B"))
                .andExpect(jsonPath("$.attempts").value(greaterThan(0)));
    }

    @Test
    @Order(9)
    @DisplayName("TC-09 | Per-processor stats available for PROC_C")
    void tc09_processorStatsC() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/processor/PROC_C"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processorId").value("PROC_C"))
                .andExpect(jsonPath("$.attempts").value(greaterThan(0)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. RETRY DECISION — SOFT DECLINES (must retry)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("TC-10 | ISSUER_TIMEOUT → RETRY_DELAYED with delay > 0")
    void tc10_issuerTimeoutShouldRetry() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_soft_001", "PROC_A",
                                FailureCode.ISSUER_TIMEOUT, PaymentCurrency.MXN, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldRetry").value(true))
                .andExpect(jsonPath("$.retryStrategy").value("RETRY_DELAYED"))
                .andExpect(jsonPath("$.retryDelaySeconds").value(greaterThan(0)))
                .andExpect(jsonPath("$.confidenceScore").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.declineType").value("SOFT"))
                .andExpect(jsonPath("$.reasoning").isNotEmpty())
                .andReturn();

        System.out.println("\n✅ TC-10 ISSUER_TIMEOUT:\n" + prettyPrint(result.getResponse().getContentAsString()));
    }

    @Test
    @Order(11)
    @DisplayName("TC-11 | RATE_LIMIT → RETRY_DELAYED with longer delay")
    void tc11_rateLimitShouldRetryWithLongerDelay() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_soft_002", "PROC_A",
                                FailureCode.RATE_LIMIT, PaymentCurrency.COP, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldRetry").value(true))
                .andExpect(jsonPath("$.retryDelaySeconds").value(greaterThanOrEqualTo(15)))
                .andReturn();

        System.out.println("\n✅ TC-11 RATE_LIMIT:\n" + prettyPrint(result.getResponse().getContentAsString()));
    }

    @Test
    @Order(12)
    @DisplayName("TC-12 | NETWORK_ERROR → retry with low delay")
    void tc12_networkErrorShouldRetry() throws Exception {
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_soft_003", "PROC_C",
                                FailureCode.NETWORK_ERROR, PaymentCurrency.CLP, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldRetry").value(true))
                .andExpect(jsonPath("$.declineType").value("SOFT"));
    }

    @Test
    @Order(13)
    @DisplayName("TC-13 | TRY_AGAIN → should retry with highest confidence")
    void tc13_tryAgainHighConfidence() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_soft_004", "PROC_A",
                                FailureCode.TRY_AGAIN, PaymentCurrency.MXN, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldRetry").value(true))
                .andExpect(jsonPath("$.confidenceScore").value(greaterThan(0.5)))
                .andReturn();

        System.out.println("\n✅ TC-13 TRY_AGAIN:\n" + prettyPrint(result.getResponse().getContentAsString()));
    }

    @Test
    @Order(14)
    @DisplayName("TC-14 | VELOCITY_CHECK → retry after longer delay")
    void tc14_velocityCheckLongerDelay() throws Exception {
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_soft_005", "PROC_B",
                                FailureCode.VELOCITY_CHECK, PaymentCurrency.COP, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldRetry").value(true))
                .andExpect(jsonPath("$.retryDelaySeconds").value(greaterThanOrEqualTo(30)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. RETRY DECISION — HARD DECLINES (must NOT retry)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("TC-15 | CARD_EXPIRED → DO_NOT_RETRY")
    void tc15_cardExpiredDoNotRetry() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_hard_001", "PROC_A",
                                FailureCode.CARD_EXPIRED, PaymentCurrency.MXN, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldRetry").value(false))
                .andExpect(jsonPath("$.retryStrategy").value("DO_NOT_RETRY"))
                .andExpect(jsonPath("$.declineType").value("HARD"))
                .andReturn();

        System.out.println("\n🚫 TC-15 CARD_EXPIRED:\n" + prettyPrint(result.getResponse().getContentAsString()));
    }

    @Test
    @Order(16)
    @DisplayName("TC-16 | INSUFFICIENT_FUNDS → DO_NOT_RETRY")
    void tc16_insufficientFundsDoNotRetry() throws Exception {
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_hard_002", "PROC_B",
                                FailureCode.INSUFFICIENT_FUNDS, PaymentCurrency.COP, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldRetry").value(false))
                .andExpect(jsonPath("$.retryStrategy").value("DO_NOT_RETRY"));
    }

    @Test
    @Order(17)
    @DisplayName("TC-17 | CARD_BLOCKED → DO_NOT_RETRY")
    void tc17_cardBlockedDoNotRetry() throws Exception {
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_hard_003", "PROC_C",
                                FailureCode.CARD_BLOCKED, PaymentCurrency.CLP, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldRetry").value(false))
                .andExpect(jsonPath("$.retryStrategy").value("DO_NOT_RETRY"));
    }

    @Test
    @Order(18)
    @DisplayName("TC-18 | INVALID_CVV → DO_NOT_RETRY")
    void tc18_invalidCvvDoNotRetry() throws Exception {
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_hard_004", "PROC_A",
                                FailureCode.INVALID_CVV, PaymentCurrency.MXN, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldRetry").value(false));
    }

    @Test
    @Order(19)
    @DisplayName("TC-19 | DO_NOT_HONOR → DO_NOT_RETRY")
    void tc19_doNotHonorDoNotRetry() throws Exception {
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_hard_005", "PROC_B",
                                FailureCode.DO_NOT_HONOR, PaymentCurrency.COP, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldRetry").value(false));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. RETRY DECISION — MAX RETRIES EXHAUSTED
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("TC-20 | Soft decline at max retry count → DO_NOT_RETRY")
    void tc20_maxRetriesExhausted() throws Exception {
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_maxretry_001", "PROC_A",
                                FailureCode.ISSUER_TIMEOUT, PaymentCurrency.MXN, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shouldRetry").value(false))
                .andExpect(jsonPath("$.retryStrategy").value("DO_NOT_RETRY"));
    }

    @Test
    @Order(21)
    @DisplayName("TC-21 | Confidence score decays with each retry attempt")
    void tc21_confidenceDecaysWithRetries() throws Exception {
        // Attempt 0
        MvcResult r0 = mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_decay_001", "PROC_A",
                                FailureCode.ISSUER_TIMEOUT, PaymentCurrency.MXN, 0)))
                .andExpect(status().isOk()).andReturn();

        // Attempt 1
        MvcResult r1 = mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_decay_002", "PROC_A",
                                FailureCode.ISSUER_TIMEOUT, PaymentCurrency.MXN, 1)))
                .andExpect(status().isOk()).andReturn();

        // Attempt 2
        MvcResult r2 = mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_decay_003", "PROC_A",
                                FailureCode.ISSUER_TIMEOUT, PaymentCurrency.MXN, 2)))
                .andExpect(status().isOk()).andReturn();

        double score0 = extractDouble(r0.getResponse().getContentAsString(), "confidenceScore");
        double score1 = extractDouble(r1.getResponse().getContentAsString(), "confidenceScore");
        double score2 = extractDouble(r2.getResponse().getContentAsString(), "confidenceScore");

        System.out.printf("%n📉 TC-21 Confidence Decay: attempt0=%.2f | attempt1=%.2f | attempt2=%.2f%n",
                score0, score1, score2);

        assertTrue(score0 > score1, "Score should decay: attempt0 > attempt1");
        assertTrue(score1 > score2, "Score should decay: attempt1 > attempt2");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. RETRY DECISION — PROCESSOR DIFFERENCES
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(22)
    @DisplayName("TC-22 | PROC_A has shorter delay than PROC_B for ISSUER_TIMEOUT")
    void tc22_processorSpecificDelay() throws Exception {
        MvcResult rA = mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_proc_a_001", "PROC_A",
                                FailureCode.ISSUER_TIMEOUT, PaymentCurrency.MXN, 0)))
                .andExpect(status().isOk()).andReturn();

        MvcResult rB = mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_proc_b_001", "PROC_B",
                                FailureCode.ISSUER_TIMEOUT, PaymentCurrency.MXN, 0)))
                .andExpect(status().isOk()).andReturn();

        int delayA = extractInt(rA.getResponse().getContentAsString(), "retryDelaySeconds");
        int delayB = extractInt(rB.getResponse().getContentAsString(), "retryDelaySeconds");

        System.out.printf("%n⚙️  TC-22 Processor Delays: PROC_A=%ds | PROC_B=%ds%n", delayA, delayB);
        assertTrue(delayA < delayB, "PROC_A should have shorter delay than PROC_B");
    }

    @Test
    @Order(23)
    @DisplayName("TC-23 | All three processors return valid retry decisions")
    void tc23_allProcessorsReturnDecisions() throws Exception {
        for (String proc : new String[]{"PROC_A", "PROC_B", "PROC_C"}) {
            mockMvc.perform(post("/api/v1/retry/decision")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("txn_allproc_" + proc, proc,
                                    FailureCode.NETWORK_ERROR, PaymentCurrency.CLP, 0)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.retryStrategy").exists())
                    .andExpect(jsonPath("$.reasoning").isNotEmpty());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. RETRY HISTORY
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(24)
    @DisplayName("TC-24 | Retry history recorded after decision call")
    void tc24_retryHistoryRecorded() throws Exception {
        String txId = "txn_history_001";

        // Submit decision
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(txId, "PROC_A",
                                FailureCode.ISSUER_TIMEOUT, PaymentCurrency.MXN, 0)))
                .andExpect(status().isOk());

        // Fetch history
        MvcResult result = mockMvc.perform(get("/api/v1/retry/decision/" + txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andReturn();

        System.out.println("\n📋 TC-24 HISTORY:\n" + prettyPrint(result.getResponse().getContentAsString()));
    }

    @Test
    @Order(25)
    @DisplayName("TC-25 | Multiple decisions for same transaction accumulate in history")
    void tc25_multipleDecisionsAccumulate() throws Exception {
        String txId = "txn_history_multi";

        // Submit 2 decisions
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(txId, "PROC_A",
                                FailureCode.ISSUER_TIMEOUT, PaymentCurrency.MXN, 0)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(txId, "PROC_A",
                                FailureCode.ISSUER_TIMEOUT, PaymentCurrency.MXN, 1)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/retry/decision/" + txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. VALIDATION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(26)
    @DisplayName("TC-26 | Missing transactionId returns 400")
    void tc26_missingTransactionId() throws Exception {
        String body = """
                {
                  "amount": 100.00,
                  "currency": "MXN",
                  "paymentMethodType": "CREDIT_CARD",
                  "processorId": "PROC_A",
                  "failureCode": "ISSUER_TIMEOUT",
                  "cardHash": "abc123def456abc123def456abc123de"
                }
                """;
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(27)
    @DisplayName("TC-27 | Negative amount returns 400")
    void tc27_negativeAmountRejected() throws Exception {
        String body = """
                {
                  "transactionId": "txn_invalid_001",
                  "amount": -500.00,
                  "currency": "MXN",
                  "paymentMethodType": "CREDIT_CARD",
                  "processorId": "PROC_A",
                  "failureCode": "ISSUER_TIMEOUT",
                  "cardHash": "abc123def456abc123def456abc123de"
                }
                """;
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(28)
    @DisplayName("TC-28 | Missing processorId returns 400")
    void tc28_missingProcessorId() throws Exception {
        String body = """
                {
                  "transactionId": "txn_invalid_002",
                  "amount": 100.00,
                  "currency": "MXN",
                  "paymentMethodType": "CREDIT_CARD",
                  "failureCode": "ISSUER_TIMEOUT",
                  "cardHash": "abc123def456abc123def456abc123de"
                }
                """;
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. AMBIGUOUS DECLINES
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(29)
    @DisplayName("TC-29 | UNKNOWN_ERROR (ambiguous) — retried with low confidence")
    void tc29_unknownErrorAmbiguous() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_ambig_001", "PROC_A",
                                FailureCode.UNKNOWN_ERROR, PaymentCurrency.MXN, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declineType").value("AMBIGUOUS"))
                .andReturn();

        double confidence = extractDouble(
                result.getResponse().getContentAsString(), "confidenceScore");
        System.out.printf("%n❓ TC-29 UNKNOWN_ERROR confidence: %.2f%n", confidence);
        assertTrue(confidence < 0.5, "Ambiguous decline should have low confidence score");
    }

    @Test
    @Order(30)
    @DisplayName("TC-30 | GENERIC_DECLINE (ambiguous) — low confidence score")
    void tc30_genericDeclineAmbiguous() throws Exception {
        mockMvc.perform(post("/api/v1/retry/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("txn_ambig_002", "PROC_B",
                                FailureCode.GENERIC_DECLINE, PaymentCurrency.COP, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declineType").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.confidenceScore").value(lessThan(0.5)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String buildRequest(String txId, String processorId,
                                FailureCode failureCode, PaymentCurrency currency,
                                int previousRetryCount) throws Exception {
        AuthorizationFailureRequest req = new AuthorizationFailureRequest();
        req.setTransactionId(txId);
        req.setAmount(new BigDecimal("1500.00"));
        req.setCurrency(currency);
        req.setPaymentMethodType(PaymentMethodType.CREDIT_CARD);
        req.setProcessorId(processorId);
        req.setFailureCode(failureCode);
        req.setCardHash("a3f2b1c4d5e6f7a8b9c0d1e2f3a4b5c6");
        req.setPreviousRetryCount(previousRetryCount);
        return objectMapper.writeValueAsString(req);
    }

    private double extractDouble(String json, String field) throws Exception {
        return objectMapper.readTree(json).get(field).asDouble();
    }

    private int extractInt(String json, String field) throws Exception {
        return objectMapper.readTree(json).get(field).asInt();
    }

    private String prettyPrint(String json) throws Exception {
        Object obj = objectMapper.readValue(json, Object.class);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }
}