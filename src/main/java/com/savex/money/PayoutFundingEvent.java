package com.savex.money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="payout_funding_events") @Getter @Setter @NoArgsConstructor
public class PayoutFundingEvent {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="provider_mode",nullable=false,length=8) private String providerMode;
 @Column(name="transfer_id",nullable=false,length=20) private String transferId;
 @Column(nullable=false,length=24) private String state="PENDING";
 @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
 @Column(name="next_check_at",nullable=false) private Instant nextCheckAt=Instant.now();
 @Version private long version;
}
