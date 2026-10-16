package com.savex.security;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface SessionTokenRepository extends JpaRepository<SessionToken,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SessionToken s where s.id=:id")
    Optional<SessionToken> lock(@Param("id") UUID id);
}
