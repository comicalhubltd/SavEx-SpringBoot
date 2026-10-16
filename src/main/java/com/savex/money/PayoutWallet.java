package com.savex.money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="payout_wallets") @Getter @Setter @NoArgsConstructor
public class PayoutWallet {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="user_id",nullable=false) private UUID userId;
 @Column(name="provider_mode",nullable=false,length=8) private String providerMode;
 @Column(nullable=false,length=24) private String state="CREATING";
 @Column(name="account_reference",length=80,unique=true) private String accountReference;
 @Column(name="barter_id",length=80) private String barterId;
 @Column(name="account_number",length=10) private String accountNumber;
 @Column(name="bank_code",length=6) private String bankCode;
 @Column(name="bank_name",length=120) private String bankName;
 @Column(name="account_name",length=100) private String accountName;
 @Column(name="balance_kobo",nullable=false) private long balanceKobo;
 @Column(name="locked_kobo",nullable=false) private long lockedKobo;
 @Column(name="provider_available_kobo") private Long providerAvailableKobo;
 @Column(name="checked_at") private Instant checkedAt;
 @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
 @Column(name="sync_requested",nullable=false) private boolean syncRequested;
 @Column(name="sync_note",length=200) private String syncNote;
 @Version private long version;
 public long available() {return Math.subtractExact(balanceKobo,lockedKobo);}
 public String availableAccount() {return "psa:"+id+":available";}
 public String heldAccount() {return "psa:"+id+":held";}
 public String clearingAccount() {return "provider:psa:"+id;}
}
