package com.savex.repository;

import com.savex.entity.Group;
import com.savex.entity.enums.GroupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Group> findByInviteCodeAndDeletedAtIsNull(String inviteCode);

    Page<Group> findByStatusAndDeletedAtIsNull(GroupStatus status, Pageable pageable);

    @Query("SELECT g FROM Group g JOIN g.members m WHERE m.user.id = :userId AND m.leftAt IS NULL AND g.deletedAt IS NULL")
    Page<Group> findGroupsByMemberId(UUID userId, Pageable pageable);

    @Query("SELECT g FROM Group g WHERE g.creator.id = :creatorId AND g.deletedAt IS NULL")
    Page<Group> findByCreatorId(UUID creatorId, Pageable pageable);
}
