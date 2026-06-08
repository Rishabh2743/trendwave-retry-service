# TrendWave Adaptive Retry Service

> **Intelligent payment authorization retry logic for TrendWave's LATAM marketplace (Mexico · Colombia · Chile)**

---

## Table of Contents

1. [Problem Statement](#problem-statement)
2. [Solution Overview](#solution-overview)
3. [Architecture](#architecture)
4. [Project Structure](#project-structure)
5. [API Reference](#api-reference)
6. [Getting Started](#getting-started)
7. [Configuration](#configuration)
8. [Retry Decision Engine](#retry-decision-engine)
9. [Simulation & Metrics](#simulation--metrics)
10. [Test Suite](#test-suite)
11. [Design Principles](#design-principles)
12. [Performance Results](#performance-results)
13. [Known Limitations & Future Improvements](#known-limitations--future-improvements)

---

## Problem Statement

TrendWave processes ~45,000 daily transactions across MXN, COP, and CLP markets. Their naive retry strategy — immediately retrying any failed authorization with the same processor — caused their auth rate to drop from **82% to 71%**, losing an estimated **$380,000 GMV per day**.

Root causes identified:

- Temporary issuer issues (rate limiting, velocity checks) were retried too fast, triggering anti-fraud rules permanently
- Hard declines (expired cards, insufficient funds) were retried wastefully, burning API quota
- Processor-specific cooldown quirks were not accounted for — PROC_A needs 5s, PROC_B needs 30s

---

## Solution Overview

This service exposes a REST API that TrendWave's checkout backend calls whenever an authorization fails. It returns an actionable retry recommendation in milliseconds.

```
POST /api/v1/retry/decision
→ { shouldRetry: true, retryStrategy: "RETRY_DELAYED", retryDelaySeconds: 5, confidenceScore: 0.68, reasoning: "..." }
```

The engine classifies failures, applies processor-specific profiles, calculates a confidence score, and returns one of four strategies:

| Strategy | When |
|---|---|
| `DO_NOT_RETRY` | Hard declines, max retries reached |
| `RETRY_IMMEDIATE` | Soft declines with delay ≤ 2s |
| `RETRY_DELAYED` | Soft declines with processor-specific delay |
| `RETRY_ALTERNATE_PROCESSOR` | Primary processor has poor recovery rate after 1+ retries |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    REST Controllers                         │
│  RetryDecisionController  SimulationController  Metrics     │
└────────────────────┬────────────────────────────────────────┘
                     │ (interfaces only — DIP)
┌────────────────────▼────────────────────────────────────────┐
│                    Service Layer                            │
│  RetryDecisionService  SimulationService  MetricsService    │
│  ProcessorProfileService                                    │
└──────┬──────────────────────────────────┬───────────────────┘
       │                                  │
┌──────▼──────────┐            ┌──────────▼──────────────────┐
│  Engine Layer   │            │      Repository Layer        │
│                 │            │                              │
│ DeclineClassi-  │            │  TransactionRepository       │
│   fier          │            │  RetryAttemptRepository      │
│                 │            └─────────────┬────────────────┘
│ RetryStrategy-  │                          │
│   Resolver      │                    ┌─────▼──────┐
│                 │                    │  H2 / JPA  │
│ ConfidenceScore-│                    └────────────┘
│   Calculator    │
└─────────────────┘
```

### Key Design Decisions

**Engine is decoupled from Service** — The three engine components (`DeclineClassifier`, `RetryStrategyResolver`, `ConfidenceScoreCalculator`) are independently injectable and testable. Swapping in an ML-backed classifier requires zero changes to `RetryDecisionServiceImpl`.

**Processor profiles live in memory** — Loaded from `application.yml` at startup into a `ConcurrentHashMap`. In production this would be Redis, allowing profile updates across instances without redeployment.

**Confidence score uses decay + velocity** — Each additional retry attempt reduces confidence by 15%. Cards with more than 3 recent attempts incur an additional velocity penalty of 5% per extra attempt.

---

## Project Structure

```
src/main/java/com/trendwave/retry_service/
├── RetryServiceApplication.java
│
├── config/
│   ├── AppConfig.java                    # @ConfigurationProperties for retry + processor settings
│   └── SwaggerConfig.java                # OpenAPI 3 metadata
│
├── controller/
│   ├── RetryDecisionController.java      # POST /retry/decision, GET /retry/decision/{id}
│   ├── SimulationController.java         # POST /simulation/run, POST /data/generate
│   └── MetricsController.java            # GET /metrics/summary, GET /metrics/processor/{id}
│
├── domain/
│   ├── enums/
│   │   ├── DeclineType.java              # HARD | SOFT | AMBIGUOUS
│   │   ├── FailureCode.java              # All failure codes with metadata (delay, recovery rate)
│   │   ├── RetryStrategy.java            # DO_NOT_RETRY | RETRY_IMMEDIATE | RETRY_DELAYED | RETRY_ALTERNATE_PROCESSOR
│   │   ├── PaymentCurrency.java          # MXN | COP | CLP
│   │   └── PaymentMethodType.java        # CREDIT_CARD | DEBIT_CARD | PREPAID_CARD
│   ├── entity/
│   │   ├── TransactionEntity.java        # JPA entity — transactions table
│   │   └── RetryAttemptEntity.java       # JPA entity — retry_attempts table
│   └── model/
│       ├── Transaction.java              # Domain model (decoupled from JPA)
│       ├── RetryAttempt.java
│       └── ProcessorProfile.java         # In-memory processor retry profile
│
├── dto/
│   ├── request/
│   │   ├── AuthorizationFailureRequest.java
│   │   └── SimulationRequest.java
│   └── response/
│       ├── RetryDecisionResponse.java
│       ├── SimulationResultResponse.java
│       └── MetricsSummaryResponse.java
│
├── engine/
│   ├── DeclineClassifier.java            # Interface: classify FailureCode → DeclineType
│   ├── RetryStrategyResolver.java        # Interface: resolve strategy + delay
│   ├── ConfidenceScoreCalculator.java    # Interface: calculate retry success probability
│   └── impl/
│       ├── DeclineClassifierImpl.java
│       ├── RetryStrategyResolverImpl.java
│       └── ConfidenceScoreCalculatorImpl.java
│
├── service/
│   ├── RetryDecisionService.java
│   ├── SimulationService.java
│   ├── MetricsService.java
│   ├── ProcessorProfileService.java
│   └── impl/
│       ├── RetryDecisionServiceImpl.java
│       ├── SimulationServiceImpl.java
│       ├── MetricsServiceImpl.java
│       └── ProcessorProfileServiceImpl.java
│
├── repository/
│   ├── TransactionRepository.java
│   └── RetryAttemptRepository.java
│
├── data/
│   └── TestDataGenerator.java            # Generates 1000+ realistic synthetic transactions
│
└── exception/
    ├── GlobalExceptionHandler.java        # RFC 9457 ProblemDetail error responses
    ├── InvalidTransactionException.java
    └── ProcessorNotFoundException.java
```

---

## API Reference

### Base URL
```
http://localhost:8080
```

### Swagger UI
```
http://localhost:8080/swagger-ui.html
```

---

### `POST /api/v1/retry/decision`

Submit a failed authorization attempt. Returns a retry recommendation.

**Request Body:**
```json
{
  "transactionId": "txn_abc123",
  "amount": 1500.00,
  "currency": "MXN",
  "paymentMethodType": "CREDIT_CARD",
  "processorId": "PROC_A",
  "failureCode": "ISSUER_TIMEOUT",
  "cardHash": "a3f2b1c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0",
  "previousRetryCount": 0
}
```

**Response:**
```json
{
  "transactionId": "txn_abc123",
  "shouldRetry": true,
  "retryStrategy": "RETRY_DELAYED",
  "retryDelaySeconds": 5,
  "alternateProcessorId": null,
  "confidenceScore": 0.68,
  "declineType": "SOFT",
  "reasoning": "Soft decline (ISSUER_TIMEOUT) — Issuer did not respond in time. Retry after 5s delay. Historical recovery rate: 68%. Recent card attempts: 0.",
  "maxRetryAttempts": 3
}
```

**Supported `failureCode` values:**

| Code | Type | Default Delay | Base Recovery Rate |
|------|------|--------------|-------------------|
| `INSUFFICIENT_FUNDS` | HARD | — | 0% |
| `CARD_EXPIRED` | HARD | — | 0% |
| `CARD_BLOCKED` | HARD | — | 0% |
| `INVALID_CVV` | HARD | — | 0% |
| `DO_NOT_HONOR` | HARD | — | 0% |
| `CARD_NOT_ACTIVATED` | HARD | — | 0% |
| `ISSUER_TIMEOUT` | SOFT | 10s | 65% |
| `RATE_LIMIT` | SOFT | 30s | 55% |
| `VELOCITY_CHECK` | SOFT | 60s | 45% |
| `TEMPORARY_PROCESSOR_ERROR` | SOFT | 15s | 60% |
| `TRY_AGAIN` | SOFT | 10s | 70% |
| `NETWORK_ERROR` | SOFT | 5s | 72% |
| `ISSUER_UNAVAILABLE` | SOFT | 20s | 58% |
| `RISK_SCORE_HIGH` | SOFT | 30s | 40% |
| `UNKNOWN_ERROR` | AMBIGUOUS | 15s | 30% |
| `GENERIC_DECLINE` | AMBIGUOUS | 20s | 25% |

---

### `GET /api/v1/retry/decision/{transactionId}`

Get the full retry decision history for a transaction.

**Response:** Array of `RetryDecisionResponse`

---

### `POST /api/v1/simulation/run`

Run the intelligent retry simulation against stored transactions. Returns before/after auth rate comparison.

**Request Body (optional):**
```json
{
  "limit": 0,
  "regenerateData": false,
  "generateCount": 1200
}
```

**Response:**
```json
{
  "totalTransactions": 1200,
  "baselineAuthRate": 0.725,
  "intelligentAuthRate": 0.843,
  "authRateImprovement": 0.118,
  "totalRetries": 284,
  "successfulRetries": 168,
  "retryEfficiency": 0.5915,
  "falseRetryRate": 0.0,
  "avgTimeToAuthSeconds": 11.8,
  "recoveredTransactions": 168,
  "statsByFailureCode": {
    "ISSUER_TIMEOUT": { "total": 52, "retried": 52, "recovered": 34, "recoveryRate": 0.65 },
    "RATE_LIMIT":     { "total": 38, "retried": 38, "recovered": 21, "recoveryRate": 0.55 }
  }
}
```

---

### `GET /api/v1/metrics/summary`

Live metrics from all stored transaction data.

---

### `GET /api/v1/metrics/processor/{processorId}`

Per-processor authorization rate and retry recovery stats.

```bash
GET /api/v1/metrics/processor/PROC_A
GET /api/v1/metrics/processor/PROC_B
GET /api/v1/metrics/processor/PROC_C
```

---

### `POST /api/v1/data/generate?count=1200`

Generate synthetic test data.

---

### `GET /actuator/health`

Health check — returns `{"status":"UP"}`.

---

## Getting Started

### Prerequisites

- Java 21
- Maven 3.8+

### 1. Clone and build

```bash
git clone https://github.com/your-org/trendwave-retry-service.git
cd trendwave-retry-service
mvn clean install -DskipTests
```

### 2. Run

```bash
mvn spring-boot:run
```

Service starts on `http://localhost:8080`.

### 3. Generate test data

```bash
curl -X POST "http://localhost:8080/api/v1/data/generate?count=1200"
```

### 4. Run simulation

```bash
curl -X POST "http://localhost:8080/api/v1/simulation/run" \
     -H "Content-Type: application/json" \
     -d "{}"
```

### 5. View metrics

```bash
curl http://localhost:8080/api/v1/metrics/summary
```

### 6. Submit a live retry decision

```bash
curl -X POST "http://localhost:8080/api/v1/retry/decision" \
     -H "Content-Type: application/json" \
     -d '{
       "transactionId": "txn_live_001",
       "amount": 1500.00,
       "currency": "MXN",
       "paymentMethodType": "CREDIT_CARD",
       "processorId": "PROC_A",
       "failureCode": "ISSUER_TIMEOUT",
       "cardHash": "a3f2b1c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0",
       "previousRetryCount": 0
     }'
```

### 7. Open H2 console (dev only)

```
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:retrydb
Username: sa
Password: (empty)
```

---

## Configuration

All retry settings live in `src/main/resources/application.yml`:

```yaml
retry:
  max-attempts: 3
  default-delay-seconds: 10
  processors:
    PROC_A:
      timeout-retry-delay-seconds: 5      # shortest delay — best performer
      max-retry-attempts: 3
      timeout-recovery-rate: 0.68
    PROC_B:
      timeout-retry-delay-seconds: 30     # slowest recovery
      max-retry-attempts: 2
      timeout-recovery-rate: 0.30
    PROC_C:
      timeout-retry-delay-seconds: 15
      max-retry-attempts: 3
      timeout-recovery-rate: 0.50
```

Adding a new processor requires only a new YAML block — zero code changes needed.

---

## Retry Decision Engine

### Decision Flow

```
AuthorizationFailureRequest
        │
        ▼
DeclineClassifier.classify()
        │
   HARD ──────────────────────────────────► DO_NOT_RETRY
        │
   SOFT / AMBIGUOUS
        │
        ▼
RetryStrategyResolver.resolve()
        │
   retryCount >= max ──────────────────────► DO_NOT_RETRY
        │
   profile.recoveryRate < 0.25
   AND retryCount >= 1 ──────────────────► RETRY_ALTERNATE_PROCESSOR
        │
   delay <= 2s ──────────────────────────► RETRY_IMMEDIATE
        │
   delay > 2s ───────────────────────────► RETRY_DELAYED
        │
        ▼
ConfidenceScoreCalculator.calculate()
        │
        ▼
RetryDecisionResponse
```

### Confidence Score Formula

```
confidence = base_recovery_rate
           - (retry_count × 0.15)
           - max(0, recent_card_attempts - 3) × 0.05

clamped to [0.0, 1.0]
```

Where `base_recovery_rate` comes from the processor's observed profile for that failure code, falling back to the failure code's built-in default.

### Processor Profile Priority

For each decision, the engine checks in this order:

1. Processor-specific delay from profile (`application.yml` + runtime learning)
2. Failure code default delay (built into `FailureCode` enum)
3. Global default (`retry.default-delay-seconds`)

---

## Simulation & Metrics

### Test Data Distribution

The generator produces realistic data matching TrendWave's observed patterns:

| Parameter | Value |
|-----------|-------|
| Baseline auth rate | 72.5% |
| Hard declines | ~40% of failures |
| Soft declines | ~50% of failures |
| Ambiguous declines | ~10% of failures |
| Processor split | PROC_A 45% · PROC_B 35% · PROC_C 20% |
| Unique cards | 300 (velocity tracking across transactions) |
| Currencies | MXN · COP · CLP |
| Random seed | Fixed at 42 (reproducible results) |

### Key Metrics Explained

| Metric | Formula | What it tells you |
|--------|---------|-------------------|
| `baselineAuthRate` | authorized / total | Auth rate with zero retries |
| `intelligentAuthRate` | (authorized + recovered) / total | Auth rate with smart retries |
| `authRateImprovement` | intelligent − baseline | Direct GMV impact |
| `retryEfficiency` | successful retries / total retries | Quality of retry decisions |
| `falseRetryRate` | hard decline retries / total retries | Wasted API calls |
| `avgTimeToAuthSeconds` | avg delay for recovered transactions | Latency cost of retry |

---

## Test Suite

30 integration tests covering every scenario:

```bash
mvn test
```

| Range | Category | What's tested |
|-------|----------|--------------|
| TC-01 | Health | Service up |
| TC-02 | Data generation | 1200 transactions created |
| TC-03–05 | Simulation | Auth rate improves, no false retries, regeneration |
| TC-06–09 | Metrics | Summary + all 3 processors |
| TC-10–14 | Soft declines | ISSUER_TIMEOUT, RATE_LIMIT, NETWORK_ERROR, TRY_AGAIN, VELOCITY_CHECK |
| TC-15–19 | Hard declines | CARD_EXPIRED, INSUFFICIENT_FUNDS, CARD_BLOCKED, INVALID_CVV, DO_NOT_HONOR |
| TC-20–21 | Retry limits | Max retries stops retry, confidence decays per attempt |
| TC-22–23 | Processor profiles | PROC_A shorter delay than PROC_B, all processors respond |
| TC-24–25 | History | Attempts recorded, multiple decisions accumulate |
| TC-26–28 | Validation | Missing fields and negative amounts return 400 |
| TC-29–30 | Ambiguous declines | Low confidence, correct type label |

---

## Design Principles

### SOLID Applied

**Single Responsibility**
Each engine component does exactly one thing: `DeclineClassifier` classifies, `RetryStrategyResolver` resolves strategy, `ConfidenceScoreCalculator` scores. None of them know about each other.

**Open/Closed**
New processors are added via `application.yml` with zero code changes. New failure codes extend `FailureCode` enum without touching any service logic.

**Liskov Substitution**
`RetryDecisionServiceImpl` can be replaced with an ML-backed implementation — all controllers depend on the `RetryDecisionService` interface and would not notice the swap.

**Interface Segregation**
`RetryDecisionService`, `SimulationService`, and `MetricsService` are separate interfaces. The simulation controller has no dependency on retry decision logic and vice versa.

**Dependency Inversion**
Controllers depend on service interfaces. Services depend on engine interfaces. Nothing in the upper layers imports a concrete `Impl` class.

### Error Handling

All errors return RFC 9457 `ProblemDetail` responses:

```json
{
  "type": "https://trendwave.com/errors/validation",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Transaction ID is required; Amount must be positive",
  "timestamp": "2025-06-08T10:23:45Z"
}
```

---

## Performance Results

Typical simulation output on 1200 transactions:

```
Total Transactions   : 1200
Baseline Auth Rate   : 72.50%   ← naive (no retries)
Intelligent Auth Rate: 84.30%   ← with smart retries
Auth Rate Improvement: +11.80%

Total Retries        : 284
Successful Retries   : 168
Retry Efficiency     : 59.15%
False Retry Rate     : 0.00%    ← no hard declines retried
Avg Time to Auth     : 11.8s
Recovered Transactions: 168
```

At TrendWave's scale (45,000 daily transactions), an 11.8% auth rate improvement translates to approximately **5,310 additional authorizations per day**.

---

## Known Limitations & Future Improvements

| Area | Current State | Production Improvement |
|------|--------------|----------------------|
| Processor profiles | In-memory `ConcurrentHashMap` | Redis — shared across service instances |
| Database | H2 in-memory | PostgreSQL with proper indexing |
| Retry outcome feedback | Not implemented | Webhook callback to mark attempts succeeded/failed |
| Auth | None | Spring Security with API key per merchant |
| Delay strategy | Fixed per failure code | Exponential backoff with jitter |
| Circuit breaker | None | Resilience4j per processor — auto-detect outages |
| Adaptive learning | Config-driven | Online learning from retry outcomes over rolling windows |
| Observability | Spring Actuator | Micrometer → Prometheus → Grafana dashboards |
| Alternate processor routing | Config-driven | Dynamic routing based on real-time recovery rates |
| Card velocity | 5-minute window | Configurable rolling window with Redis TTL |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Persistence | Spring Data JPA + H2 |
| Validation | Jakarta Bean Validation |
| API Docs | SpringDoc OpenAPI 2.8.6 (Swagger UI) |
| Boilerplate | Lombok |
| Mapping | MapStruct |
| Testing | JUnit 5 + Spring MockMvc |
| Build | Maven |

---

*Built for TrendWave · Powered by Yuno payment orchestration*
