package com.savex.entity;

import com.savex.entity.enums.KycStatus;
import com.savex.entity.enums.UserRole;
import com.savex.entity.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(length = 11)
    private String bvn;

    @Column(name = "profile_photo")
    private String profilePhoto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id")
    private State state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lga_id")
    private Lga lga;

    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_level", nullable = false)
    @Builder.Default
    private KycStatus kycLevel = KycStatus.UNVERIFIED;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "email_account_approved_at")
    private Instant emailAccountApprovedAt;

    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private boolean phoneVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isActive() {
        return status == UserStatus.ACTIVE && deletedAt == null;
    }
}
