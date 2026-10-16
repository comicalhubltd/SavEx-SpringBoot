package com.savex.money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="payout_wallet_recoveries") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PayoutWalletRecovery {
 @Id private UUID id;
 @Column(name="wallet_id",nullable=false) private UUID walletId;
 @Column(name="reviewer_id",nullable=false) private UUID reviewerId;
 @Column(name="account_reference",nullable=false,length=80) private String accountReference;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 public PayoutWalletRecovery(UUID wallet,UUID reviewer,String reference) {id=UUID.randomUUID();walletId=wallet;reviewerId=reviewer;accountReference=reference;createdAt=Instant.now();}
}
