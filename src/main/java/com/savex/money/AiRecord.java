package com.savex.money;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="ai_records") @Getter @Setter @NoArgsConstructor
public class AiRecord {
    @Id private UUID id=UUID.randomUUID();
    @Column(nullable=false,length=16) private String kind;
    @Column(nullable=false,length=32) private String state;
    @Column(name="record_key",unique=true,length=120) private String recordKey;
    @Column(name="actor_id") private UUID actorId;
    @Column(name="parent_id") private UUID parentId;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    @Column(name="reviewed_by") private UUID reviewedBy;
    @Column(name="reviewed_at") private Instant reviewedAt;
    @Column(nullable=false,columnDefinition="TEXT") private String payload;
    @Version private long version;
    public AiRecord(String kind,String state,String key,UUID actor,UUID parent,String payload){
        this.kind=kind;this.state=state;this.recordKey=key;this.actorId=actor;this.parentId=parent;this.payload=payload;
    }
}
