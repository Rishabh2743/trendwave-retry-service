package com.trendwave.retry_service.domain.model;

import com.trendwave.retry_service.domain.enums.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** Domain model — decoupled from the JPA entity */
@Data
@Builder
public class Transaction {
    private String id;
    private BigDecimal amount;
    private PaymentCurrency currency;
    private PaymentMethodType paymentMethodType;
    private String processorId;
    private FailureCode failureCode;
    private DeclineType declineType;
    private String cardHash;
    private boolean authorized;
    private int retryCount;
    private Instant createdAt;
}