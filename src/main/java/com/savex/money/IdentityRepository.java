package com.savex.money;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface IdentityRepository extends JpaRepository<IdentityCheck,UUID> {
    Optional<IdentityCheck> findByUserId(UUID userId);
}
