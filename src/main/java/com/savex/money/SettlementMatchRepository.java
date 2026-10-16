package com.savex.money;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SettlementMatchRepository extends JpaRepository<SettlementMatch,UUID> {
    Optional<SettlementMatch> findByOrderId(UUID id);
}
