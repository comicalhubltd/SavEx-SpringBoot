package com.savex.repository;

import com.savex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    org.springframework.data.domain.Page<User> findAllByDeletedAtIsNull(org.springframework.data.domain.Pageable pageable);
    boolean existsByRoleAndStatusAndDeletedAtIsNull(com.savex.entity.enums.UserRole role,com.savex.entity.enums.UserStatus status);
    org.springframework.data.domain.Page<User> findByStatusAndDeletedAtIsNull(com.savex.entity.enums.UserStatus status,org.springframework.data.domain.Pageable pageable);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByPhoneAndDeletedAtIsNull(String phone);

    @Query("SELECT u FROM User u WHERE (u.email = :identifier OR u.phone = :identifier) AND u.deletedAt IS NULL")
    Optional<User> findByEmailOrPhone(String identifier);
}
