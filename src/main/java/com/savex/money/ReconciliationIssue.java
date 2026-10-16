package com.savex.money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="reconciliation_issues") @Getter @Setter @NoArgsConstructor
public class ReconciliationIssue {
    @Id private UUID id=UUID.randomUUID();
    @Column(name="issue_key",nullable=false,unique=true,length=160) private String issueKey;
    @Column(name="order_id") private UUID orderId;
    @Column(nullable=false,length=60) private String reason;
    @Column(nullable=false,length=16) private String status="OPEN";
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    @Column(name="resolved_at") private Instant resolvedAt;
    @Column(name="reviewer_id") private UUID reviewerId;
    @Column(name="evidence_reference",length=200) private String evidenceReference;
    public ReconciliationIssue(String key,UUID order,String reason) {issueKey=key;orderId=order;this.reason=reason;}
}
