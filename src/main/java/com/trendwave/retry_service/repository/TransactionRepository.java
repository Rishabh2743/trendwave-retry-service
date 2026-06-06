package com.trendwave.retry_service.repository;

import com.trendwave.retry_service.domain.entity.TransactionEntity;
import com.trendwave.retry_service.domain.enums.DeclineType;
import com.trendwave.retry_service.domain.enums.FailureCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

    List<TransactionEntity> findByCardHashAndCreatedAtAfter(String cardHash, Instant since);

    List<TransactionEntity> findByProcessorId(String processorId);

    List<TransactionEntity> findByDeclineType(DeclineType declineType);

    @Query("SELECT t FROM TransactionEntity t WHERE t.processorId = :processorId AND t.failureCode = :failureCode")
    List<TransactionEntity> findByProcessorAndFailureCode(
            @Param("processorId") String processorId,
            @Param("failureCode") FailureCode failureCode);

    @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.cardHash = :cardHash AND t.createdAt > :since")
    long countRecentAttemptsByCard(@Param("cardHash") String cardHash, @Param("since") Instant since);

    long countByAuthorized(boolean authorized);

    long countByDeclineType(DeclineType declineType);
}