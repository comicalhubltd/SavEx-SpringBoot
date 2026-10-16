package com.savex.money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="identity_checks") @Getter @Setter @NoArgsConstructor
public class IdentityCheck {
    @Id private UUID id=UUID.randomUUID();
    @Column(name="user_id",nullable=false,unique=true) private UUID userId;
    @Column(name="provider_reference",length=120,unique=true) private String providerReference;
    @Column(name="consent_url",length=1000) private String consentUrl;
    @Column(nullable=false,length=32) private String status="STARTING";
    @Column(name="updated_at",nullable=false) private Instant updatedAt=Instant.now();
    @Version private long version;
    @Column(name="provider_mode",nullable=false,length=8) private String providerMode="UNKNOWN";
}
