package com.trendwave.retry_service.domain.entity;

import com.trendwave.retry_service.domain.enums.RetryStrategy;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "retry_attempts",
       indexes = {
           @Index(name = "idx_retry_tx_id", columnList = "transaction_id"),
           @Index(name = "idx_retry_created", columnList = "createdAt")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryAttemptEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionEntity transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RetryStrategy strategy;

    @Column(nullable = false)
    private int attemptNumber;

    @Column(nullable = false)
    private int delaySeconds;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(nullable = false)
    private double confidenceScore;

    @Column(length = 500)
    private String reasoning;

    @Column(length = 50)
    private String alternateProcessorId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}