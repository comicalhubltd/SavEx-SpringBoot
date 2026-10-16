package com.savex.money;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface PayoutFundingEventRepository extends JpaRepository<PayoutFundingEvent,UUID> {
 boolean existsByProviderModeAndTransferId(String mode,String transfer);
 List<PayoutFundingEvent> findByProviderModeAndStateAndNextCheckAtBeforeOrderByCreatedAtAsc(String mode,String state,Instant now,Pageable page);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from PayoutFundingEvent e where e.id=:id")
 Optional<PayoutFundingEvent> lock(@Param("id") UUID id);
}
