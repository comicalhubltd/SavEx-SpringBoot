package com.savex.repository;

import com.savex.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    java.util.List<Wallet> findByUser_IdIn(java.util.Collection<UUID> ids);

    Optional<Wallet> findByUserId(UUID userId);

    @Modifying
    @Query("UPDATE Wallet w SET w.balanceKobo = w.balanceKobo + :amount WHERE w.user.id = :userId")
    int creditBalance(UUID userId, long amount);

    @Modifying
    @Query("UPDATE Wallet w SET w.balanceKobo = w.balanceKobo - :amount WHERE w.user.id = :userId AND w.balanceKobo - w.lockedKobo >= :amount")
    int debitBalance(UUID userId, long amount);
}
