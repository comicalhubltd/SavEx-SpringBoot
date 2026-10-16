package com.savex.repository;

import com.savex.entity.Transaction;
import com.savex.entity.enums.TransactionStatus;
import com.savex.entity.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReference(String reference);

    Page<Transaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Transaction> findByGroupIdOrderByCreatedAtDesc(UUID groupId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amountKobo), 0) FROM Transaction t WHERE t.payoutCycle.id = :cycleId AND t.type = :type AND t.status = :status")
    long sumAmountByPayoutCycleAndTypeAndStatus(UUID cycleId, TransactionType type, TransactionStatus status);
}
