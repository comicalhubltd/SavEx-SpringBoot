package com.savex.money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="money_orders",uniqueConstraints={
 @UniqueConstraint(columnNames={"user_id","idempotency_key"}),@UniqueConstraint(columnNames="business_key")})
@Getter @Setter @NoArgsConstructor
public class MoneyOrder {
    public enum Kind { DEPOSIT, WITHDRAWAL, AIRTIME, DATA, ELECTRICITY, CABLE, INTERNET, CONTRIBUTION, PAYOUT }
    public enum Status { QUEUED, SUBMITTING, PENDING, SUCCESS, FAILED, REVERSED, REVIEW }
    @Id private UUID id = UUID.randomUUID();
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(name="idempotency_key",nullable=false,length=80) private String idempotencyKey;
    @Column(nullable=false,length=64) private String fingerprint;
    @Column(name="business_key",length=120) private String businessKey;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(nullable=false,length=24) private Kind kind;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(nullable=false,length=24) private Status status = Status.QUEUED;
    @Column(nullable=false,length=3) private String currency = "NGN";
    @Column(name="amount_kobo",nullable=false) private long amountKobo;
    @Column(name="application_fee_kobo",nullable=false) private long applicationFeeKobo;
    @Column(name="provider_amount_kobo",nullable=false) private long providerAmountKobo;
    @Column(name="provider_fee_kobo",nullable=false) private long providerFeeKobo;
    @Column(name="provider_mode",nullable=false,length=8) private String providerMode="UNKNOWN";
    public long providerAmount() { return providerAmountKobo==0?amountKobo:providerAmountKobo; }
    public long totalDebit() { return Math.addExact(amountKobo,applicationFeeKobo); }
    @Column(nullable=false,unique=true,length=80) private String reference;
    @Column(length=120) private String destination;
    @Column(name="service_id",length=40) private String serviceId;
    @Column(name="variation_code",length=120) private String variationCode;
    @Column(name="provider_reference",length=120) private String providerReference;
    @Column(name="debit_subaccount",length=80) private String debitSubaccount;
    @Column(name="recharge_token",length=256) private String rechargeToken;
    @Column(name="checkout_url",length=600) private String checkoutUrl;
    @Column(name="group_id") private UUID groupId;
    @Column(name="cycle_number") private Integer cycleNumber;
    @Column(name="next_check_at",nullable=false) private Instant nextCheckAt = Instant.now();
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt = Instant.now();
    @Column(name="updated_at",nullable=false) private Instant updatedAt = Instant.now();
    @Version private long version;
    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
