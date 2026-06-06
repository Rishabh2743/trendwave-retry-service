package com.trendwave.retry_service.dto.request;

import com.trendwave.retry_service.domain.enums.PaymentCurrency;
import com.trendwave.retry_service.domain.enums.FailureCode;
import com.trendwave.retry_service.domain.enums.PaymentMethodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Failed authorization attempt submitted for retry analysis")
public class AuthorizationFailureRequest {

    @NotBlank(message = "Transaction ID is required")
    @Schema(description = "Unique transaction identifier", example = "txn_abc123")
    private String transactionId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Transaction amount", example = "1500.00")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    @Schema(description = "ISO currency code", example = "MXN")
    private PaymentCurrency currency;

    @NotNull(message = "Payment method type is required")
    @Schema(description = "Type of payment method", example = "CREDIT_CARD")
    private PaymentMethodType paymentMethodType;

    @NotBlank(message = "Processor ID is required")
    @Schema(description = "Payment processor identifier", example = "PROC_A")
    private String processorId;

    @NotNull(message = "Failure code is required")
    @Schema(description = "Specific failure code from the processor", example = "ISSUER_TIMEOUT")
    private FailureCode failureCode;

    @NotBlank(message = "Card hash is required")
    @Size(min = 8, max = 64, message = "Card hash must be 8-64 characters")
    @Schema(description = "SHA-256 hash of card PAN for velocity checks", example = "a3f2b1...")
    private String cardHash;

    @Schema(description = "Number of previous retry attempts for this transaction", example = "0")
    private int previousRetryCount;
}