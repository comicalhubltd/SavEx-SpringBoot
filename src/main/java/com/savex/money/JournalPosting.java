package com.savex.money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
/** One immutable, balanced debit/credit pair. Corrections require a new reversing posting. */
@Entity @Table(name="journal_postings") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class JournalPosting {
    @Id private UUID id;
    @Column(name="posting_key",nullable=false,unique=true,length=120) private String postingKey;
    @Column(name="debit_account",nullable=false,length=120) private String debitAccount;
    @Column(name="credit_account",nullable=false,length=120) private String creditAccount;
    @Column(name="amount_kobo",nullable=false) private long amountKobo;
    @Column(nullable=false,length=3) private String currency;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    public JournalPosting(String key,String debit,String credit,long amount) {
        if (amount <= 0 || debit.equals(credit)) throw new IllegalArgumentException("Invalid journal posting");
        id=UUID.randomUUID(); postingKey=key; debitAccount=debit; creditAccount=credit;
        amountKobo=amount; currency="NGN"; createdAt=Instant.now();
    }
}
