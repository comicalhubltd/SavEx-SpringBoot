package com.savex.entity;

import com.savex.entity.enums.CycleFrequency;
import com.savex.entity.enums.GroupStatus;
import com.savex.entity.enums.PayoutOrder;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "`groups`")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @Column(name = "contribution_kobo", nullable = false)
    private long contributionKobo;

    @Column(name = "max_members", nullable = false)
    private short maxMembers;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_frequency", nullable = false)
    @Builder.Default
    private CycleFrequency cycleFrequency = CycleFrequency.MONTHLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_order", nullable = false)
    @Builder.Default
    private PayoutOrder payoutOrder = PayoutOrder.SEQUENTIAL;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GroupStatus status = GroupStatus.OPEN;

    @Column(name = "invite_code", nullable = false, unique = true, length = 8)
    private String inviteCode;

    @Column(name = "total_cycles", nullable = false)
    private short totalCycles;

    @Column(name = "current_cycle", nullable = false)
    @Builder.Default
    private short currentCycle = 0;

    private String rules;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GroupMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL)
    @Builder.Default
    private List<PayoutCycle> payoutCycles = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public int getMemberCount() {
        return (int) members.stream().filter(m -> m.getLeftAt() == null).count();
    }

    public boolean isFull() {
        return getMemberCount() >= maxMembers;
    }

    public double getContributionNaira() {
        return contributionKobo / 100.0;
    }
}
