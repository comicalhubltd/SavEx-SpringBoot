package com.savex.money;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ReconciliationIssueRepository extends JpaRepository<ReconciliationIssue,UUID> {
    boolean existsByIssueKey(String key);
    List<ReconciliationIssue> findTop100ByStatusOrderByCreatedAtAsc(String status);
}
