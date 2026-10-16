package com.savex.verification;

import com.savex.entity.User;
import com.savex.entity.enums.UserStatus;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

/** Email-only account activation; never changes identity or grants administrator access. */
@Service @RequiredArgsConstructor
public class AccountApprovalService {
    private final EmailChallengeRepository challenges;
    @PersistenceContext private EntityManager em;

    public static boolean validDetails(User user) {
        return user.getFullName()!=null && user.getFullName().trim().length()>=2 && user.getFullName().length()<=100
            && user.getEmail()!=null && user.getEmail().length()<=150 && user.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
            && user.getPhone()!=null && user.getPhone().matches("\\+234[789][01][0-9]{8}");
    }

    @Transactional public User approveExisting(UUID id) {
        User user=em.find(User.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(user!=null) {
            // Login may have loaded this entity before acquiring the lock.
            em.refresh(user,LockModeType.PESSIMISTIC_WRITE);
            approve(user,challenges.findById(id).orElse(null));
        }
        return user;
    }

    // Caller holds the customer row lock and commits this change with the email proof.
    public void approve(User user,EmailChallenge proof) {
        if(user.getDeletedAt()==null && user.getStatus()==UserStatus.PENDING_VERIFICATION
            && validDetails(user) && user.isEmailVerified() && proof!=null && proof.getVerifiedAt()!=null
            && user.getEmail().equals(proof.getEmail())) {
            user.setEmailAccountApprovedAt(Instant.now());
            user.setStatus(UserStatus.ACTIVE);
        }
    }
}
