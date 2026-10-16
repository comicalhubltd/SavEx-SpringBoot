package com.savex.money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="settlement_matches") @Getter @NoArgsConstructor
public class SettlementMatch {
    @Id private UUID id=UUID.randomUUID();
    @Column(name="order_id",nullable=false,unique=true) private UUID orderId;
    @Column(name="settlement_id",nullable=false,length=40) private String settlementId;
    @Column(name="gross_kobo",nullable=false) private long grossKobo;
    @Column(name="provider_fee_kobo",nullable=false) private long providerFeeKobo;
    @Column(name="matched_at",nullable=false) private Instant matchedAt=Instant.now();
    public SettlementMatch(UUID order,String settlement,long gross,long fee) {
        orderId=order;settlementId=settlement;grossKobo=gross;providerFeeKobo=fee;
    }
}
