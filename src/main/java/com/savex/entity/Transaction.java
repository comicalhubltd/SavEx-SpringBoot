package com.savex.entity;

import com.savex.entity.enums.TransactionChannel;
import com.savex.entity.enums.TransactionStatus;
import com.savex.entity.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_cycle_id")
    private PayoutCycle payoutCycle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionChannel channel = TransactionChannel.WALLET;

    @Column(name = "amount_kobo", nullable = false)
    private long amountKobo;

    @Column(name = "fee_kobo", nullable = false)
    @Builder.Default
    private long feeKobo = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(nullable = false, unique = true, length = 100)
    private String reference;

    @Column(name = "external_ref", length = 200)
    private String externalRef;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public double getAmountNaira() {
        return amountKobo / 100.0;
    }
}
