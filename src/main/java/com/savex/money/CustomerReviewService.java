package com.savex.money;
import com.savex.entity.User;
import com.savex.entity.enums.*;
import com.savex.repository.UserRepository;
import com.savex.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

/** Approval records references to externally checked evidence, never invents verification. */
@Service @RequiredArgsConstructor
public class CustomerReviewService {
    private final UserRepository users;
    private final CustomerReviewRepository reviews;
    private final IdentityRepository identities;
    private final PasswordEncoder passwords;
    private final MoneySettings settings;
    private final com.savex.verification.EmailChallengeRepository emailChallenges;
    @PersistenceContext private EntityManager em;
    @Transactional
    public void review(UUID reviewerId,UUID userId,String password,String evidence,boolean emailChecked,
                       boolean phoneChecked,boolean identityChecked,boolean approve) {
        review(reviewerId,userId,password,evidence,emailChecked,identityChecked,approve,"BVN_CONSENT",null,settings.getMode().name());
    }
    @Transactional
    public void review(UUID reviewerId,UUID userId,String password,String evidence,boolean emailChecked,
                       boolean identityChecked,boolean approve,String method,String notes,String mode) {
        if(mode==null || !java.util.Set.of("TEST","LIVE").contains(mode)) throw new BadRequestException("Select TEST or LIVE for the review");
        if(method==null || !java.util.Set.of("DOCUMENT_REVIEW","BVN_CONSENT").contains(method)) throw new BadRequestException("Select an identity evidence method");
        if("DOCUMENT_REVIEW".equals(method) && (notes==null || notes.trim().length()<20 || notes.length()>2000))
            throw new BadRequestException("Record a review report of 20 to 2000 characters describing the checks and findings");
        User reviewer=users.findById(reviewerId).orElseThrow();
        if(!reviewer.isActive() || reviewer.getRole()!=UserRole.ADMIN || reviewerId.equals(userId)
            || password==null || !passwords.matches(password,reviewer.getPasswordHash()))
            throw new UnauthorizedException("An independent administrator must authorise this review");
        if(evidence==null || evidence.isBlank() || evidence.length()>200)
            throw new BadRequestException("Supply a retained verification evidence reference");
        User customer=em.find(User.class,userId,LockModeType.PESSIMISTIC_WRITE);
        if(customer==null) throw new NotFoundException("User not found");
        if(customer.getDeletedAt()!=null || customer.getStatus()==UserStatus.SUSPENDED
            || customer.getStatus()==UserStatus.DEACTIVATED)
            throw new BadRequestException("Suspended or closed accounts cannot be activated by identity review");
        if(approve) {
            var email=emailChallenges.findById(userId).orElse(null);
            if(!emailChecked || !identityChecked || !customer.isEmailVerified() || email==null
                || email.getVerifiedAt()==null || !customer.getEmail().equals(email.getEmail()))
                throw new BadRequestException("Complete the SavEx email code check and review identity evidence before approval");
            if("BVN_CONSENT".equals(method)) {
                var identity=identities.findByUserId(userId).orElseThrow(()->new BadRequestException("BVN consent evidence is required"));
                if(!"EVIDENCE_RECEIVED".equals(identity.getStatus())) throw new BadRequestException("Provider BVN evidence has not been received");
                if(!mode.equals(identity.getProviderMode())) throw new BadRequestException("Identity evidence must match the review environment");
            }
            // Phone ownership is optional; email and provider identity remain separate checks.
            customer.setKycLevel("BVN_CONSENT".equals(method)?KycStatus.BVN_VERIFIED:KycStatus.REVIEWED);customer.setStatus(UserStatus.ACTIVE);
        }
        else if(customer.isActive()) customer.setStatus(UserStatus.SUSPENDED);
        reviews.save(new CustomerReview(userId,reviewerId,approve?"APPROVED":"REJECTED",evidence,mode,method,notes));
    }
}
