package com.trendwave.retry_service.repository;

import com.trendwave.retry_service.domain.entity.RetryAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RetryAttemptRepository extends JpaRepository<RetryAttemptEntity, String> {

    List<RetryAttemptEntity> findByTransactionId(String transactionId);

    @Query("SELECT r FROM RetryAttemptEntity r WHERE r.transaction.processorId = :processorId")
    List<RetryAttemptEntity> findByProcessorId(@Param("processorId") String processorId);

    long countBySucceeded(boolean succeeded);

    @Query("SELECT COUNT(r) FROM RetryAttemptEntity r WHERE r.transaction.declineType = 'HARD'")
    long countRetriesOnHardDeclines();
}