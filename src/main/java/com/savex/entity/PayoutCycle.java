package com.savex.entity;

import com.savex.entity.enums.CycleStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payout_cycles",
       uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "cycle_number"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "cycle_number", nullable = false)
    private short cycleNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "total_kobo", nullable = false)
    @Builder.Default
    private long totalKobo = 0;

    @Column(name = "paid_out_at")
    private Instant paidOutAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CycleStatus status = CycleStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
