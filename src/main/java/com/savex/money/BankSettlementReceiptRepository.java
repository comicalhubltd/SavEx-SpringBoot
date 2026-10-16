package com.savex.money;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface BankSettlementReceiptRepository extends JpaRepository<BankSettlementReceipt,UUID> {
    boolean existsByOrderId(UUID order);
}
