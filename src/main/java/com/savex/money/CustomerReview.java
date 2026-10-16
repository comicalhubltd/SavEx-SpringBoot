package com.savex.money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="customer_reviews") @Getter @NoArgsConstructor
public class CustomerReview {
    @Id private UUID id=UUID.randomUUID();
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(name="reviewer_id",nullable=false) private UUID reviewerId;
    @Column(nullable=false,length=16) private String decision;
    @Column(name="evidence_reference",nullable=false,length=200) private String evidenceReference;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    @Column(name="provider_mode",nullable=false,length=8) private String providerMode="UNKNOWN";
    @Column(name="identity_method",nullable=false,length=24) private String identityMethod="BVN_CONSENT";
    @Column(name="review_notes",length=2000) private String reviewNotes;
    public CustomerReview(UUID user,UUID reviewer,String decision,String evidence,String mode) {
        userId=user;reviewerId=reviewer;this.decision=decision;evidenceReference=evidence;
        providerMode=mode;
    }
    public CustomerReview(UUID user,UUID reviewer,String decision,String evidence,String mode,String method,String notes) {
        this(user,reviewer,decision,evidence,mode);identityMethod=method;reviewNotes=notes;
    }
}
