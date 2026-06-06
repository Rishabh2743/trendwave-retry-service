package com.trendwave.retry_service.domain.entity;

import com.trendwave.retry_service.domain.enums.PaymentCurrency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import com.trendwave.retry_service.domain.enums.DeclineType;
import com.trendwave.retry_service.domain.enums.FailureCode;
import com.trendwave.retry_service.domain.enums.PaymentMethodType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "transactions",
       indexes = {
           @Index(name = "idx_card_hash", columnList = "cardHash"),
           @Index(name = "idx_processor_id", columnList = "processorId"),
           @Index(name = "idx_created_at", columnList = "createdAt")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private PaymentCurrency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethodType paymentMethodType;

    @Column(nullable = false, length = 50)
    private String processorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FailureCode failureCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeclineType declineType;

    /** Hashed card identifier for velocity checks — never store raw PAN */
    @Column(nullable = false, length = 64)
    private String cardHash;

    @Column(nullable = false)
    private boolean authorized;

    @Column(nullable = false)
    private int retryCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RetryAttemptEntity> retryAttempts = new ArrayList<>();
}