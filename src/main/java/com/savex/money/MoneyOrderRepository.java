package com.savex.money;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
public interface MoneyOrderRepository extends JpaRepository<MoneyOrder,UUID> {
    interface ModePresence { UUID getUserId(); String getProviderMode(); }
    @Query("select distinct o.userId as userId,o.providerMode as providerMode from MoneyOrder o where o.userId in :ids")
    List<ModePresence> modesForUsers(@Param("ids") Collection<UUID> ids);
    Optional<MoneyOrder> findByUserIdAndIdempotencyKey(UUID userId,String key);
    Optional<MoneyOrder> findByReference(String reference);
    List<MoneyOrder> findByReferenceIn(Collection<String> references);
    @Query("select o from MoneyOrder o where o.providerMode=:mode and o.kind=:kind and o.createdAt>=:from and o.createdAt<:to order by o.createdAt desc,o.id")
    List<MoneyOrder> historyWindow(@Param("mode") String mode,@Param("kind") MoneyOrder.Kind kind,@Param("from") Instant from,@Param("to") Instant to,Pageable page);
    boolean existsByUserIdAndProviderModeNot(UUID userId,String mode);
    boolean existsByUserIdAndProviderMode(UUID userId,String mode);
    Optional<MoneyOrder> findByBusinessKey(String key);
    @Query("select coalesce(sum(o.amountKobo + o.providerFeeKobo),0) from MoneyOrder o where o.debitSubaccount=:ref and o.kind=com.savex.money.MoneyOrder.Kind.WITHDRAWAL and o.status in :states")
    long payoutReserved(@Param("ref") String ref,@Param("states") List<MoneyOrder.Status> states);
    @Query("select coalesce(sum(o.providerFeeKobo - o.applicationFeeKobo),0) from MoneyOrder o where o.debitSubaccount=:ref and o.kind=com.savex.money.MoneyOrder.Kind.WITHDRAWAL and o.status in :states")
    long payoutFeeExposure(@Param("ref") String ref,@Param("states") List<MoneyOrder.Status> states);
    Page<MoneyOrder> findByUserIdOrderByCreatedAtDesc(UUID userId,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from MoneyOrder o where o.id=:id")
    Optional<MoneyOrder> lock(@Param("id") UUID id);
    @Query("select o.id from MoneyOrder o where o.status in :statuses and o.providerMode=:mode and o.nextCheckAt <= :now and (o.debitSubaccount is null or o.kind<>com.savex.money.MoneyOrder.Kind.DEPOSIT) order by o.nextCheckAt")
    List<UUID> due(@Param("statuses") List<MoneyOrder.Status> statuses,@Param("now") Instant now,@Param("mode") String mode,Pageable page);
    @Query("select coalesce(sum(o.amountKobo + o.applicationFeeKobo),0) from MoneyOrder o where o.userId=:userId and o.createdAt>=:since and o.kind in :kinds and o.status not in :excluded")
    long debits(@Param("userId") UUID userId,@Param("since") Instant since,@Param("kinds") List<MoneyOrder.Kind> kinds,@Param("excluded") List<MoneyOrder.Status> excluded);
    long countByGroupIdAndCycleNumberAndKindAndStatus(UUID groupId,Integer cycle,MoneyOrder.Kind kind,MoneyOrder.Status status);
}
