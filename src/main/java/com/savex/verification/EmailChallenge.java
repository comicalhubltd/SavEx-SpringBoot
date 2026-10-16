package com.savex.verification;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="email_challenges") @Getter @Setter @NoArgsConstructor
public class EmailChallenge {
    @Id @Column(name="user_id") private UUID userId;
    @Column(nullable=false,length=150) private String email;
    @Column(name="code_hash",length=64) private String codeHash;
    @Column(length=36) private String nonce;
    @Column(name="expires_at") private Instant expiresAt;
    @Column(name="sent_at") private Instant sentAt;
    @Column(name="window_started_at") private Instant windowStartedAt;
    @Column(name="send_count",nullable=false) private int sendCount;
    @Column(nullable=false) private int attempts;
    @Column(name="verified_at") private Instant verifiedAt;
}
