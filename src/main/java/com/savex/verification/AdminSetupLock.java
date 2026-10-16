package com.savex.verification;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="admin_setup_lock") @Getter @Setter @NoArgsConstructor
public class AdminSetupLock {
    @Id private Integer id;
    @Column(name="administrator_id") private UUID administratorId;
    @Column(name="completed_at") private Instant completedAt;
}
