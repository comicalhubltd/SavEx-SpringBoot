package com.savex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "payout_position")
    private Short payoutPosition;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    public boolean isActive() {
        return leftAt == null;
    }
}
