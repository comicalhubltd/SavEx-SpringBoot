package com.savex.money;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PayoutWalletRepository extends JpaRepository<PayoutWallet,UUID> {
 Optional<PayoutWallet> findByUserIdAndProviderMode(UUID user,String mode);
 Optional<PayoutWallet> findByProviderModeAndAccountNumber(String mode,String number);
 Optional<PayoutWallet> findByAccountReference(String reference);
 List<PayoutWallet> findByProviderModeAndStateOrderByCreatedAtAsc(String mode,String state,Pageable page);
 @Query("select p from PayoutWallet p where p.providerMode=:mode and p.state='ACTIVE' and (p.syncRequested=true or p.checkedAt is null or p.checkedAt<:before) order by p.checkedAt,p.createdAt")
 List<PayoutWallet> due(@Param("mode") String mode,@Param("before") java.time.Instant before,Pageable page);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from PayoutWallet p where p.id=:id")
 Optional<PayoutWallet> lock(@Param("id") UUID id);
}
