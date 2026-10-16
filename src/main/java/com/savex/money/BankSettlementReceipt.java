package com.savex.money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="bank_settlement_receipts") @Getter @NoArgsConstructor
public class BankSettlementReceipt {
    @Id private UUID id=UUID.randomUUID();
    @Column(name="order_id",nullable=false,unique=true) private UUID orderId;
    @Column(name="reviewer_id",nullable=false) private UUID reviewerId;
    @Column(name="net_kobo",nullable=false) private long netKobo;
    @Column(name="evidence_reference",nullable=false,length=200) private String evidenceReference;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    public BankSettlementReceipt(UUID order,UUID reviewer,long net,String evidence) {
        orderId=order;reviewerId=reviewer;netKobo=net;evidenceReference=evidence;
    }
}
