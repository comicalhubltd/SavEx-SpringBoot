package com.savex.security;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
/** Maps the existing refresh_tokens table. Raw credentials are never persisted. */
@Entity @Table(name="refresh_tokens") @Getter @Setter @NoArgsConstructor
public class SessionToken {
    @Id private UUID id=UUID.randomUUID();
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(name="token_hash",nullable=false,unique=true,length=64) private String tokenHash;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(nullable=false) private boolean revoked;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
}
