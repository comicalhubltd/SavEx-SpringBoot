package com.savex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "balance_kobo", nullable = false)
    @Builder.Default
    private long balanceKobo = 0;

    @Column(name = "locked_kobo", nullable = false)
    @Builder.Default
    private long lockedKobo = 0;

    @Column(name = "paystack_ref")
    private String paystackRef;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public long getAvailableKobo() {
        return balanceKobo - lockedKobo;
    }

    public double getBalanceNaira() {
        return balanceKobo / 100.0;
    }

    public double getAvailableNaira() {
        return getAvailableKobo() / 100.0;
    }
}
