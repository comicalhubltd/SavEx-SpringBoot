package com.savex.money;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface AiRecordRepository extends JpaRepository<AiRecord,UUID> {
    List<AiRecord> findTop100ByKindOrderByCreatedAtDesc(String kind);
    List<AiRecord> findTop10ByKindAndStateOrderByCreatedAtDesc(String kind,String state);
    List<AiRecord> findTop1000ByKindAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(String kind,Instant from,Instant to);
    long countByKindAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String kind,Instant from,Instant to);
    boolean existsByRecordKey(String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AiRecord a where a.id=:id")
    Optional<AiRecord> lock(@Param("id") UUID id);
}
