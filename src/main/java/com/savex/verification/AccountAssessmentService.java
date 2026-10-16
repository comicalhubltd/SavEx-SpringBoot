package com.savex.verification;
import com.savex.entity.User;
import com.savex.entity.enums.*;
import com.savex.repository.*;
import com.savex.money.*;
import com.savex.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

/** Read-only automated account checks. Self-entered details never certify identity. */
@Service @RequiredArgsConstructor
public class AccountAssessmentService {
    private final UserRepository users;
    private final EmailChallengeRepository emails;
    private final CustomerReviewRepository reviews;
    private final MoneyOrderRepository orders;
    private final WalletRepository wallets;
    private final MoneySettings settings;
    public record Check(String name,boolean passed,String finding) {}
    public record Assessment(UUID reportId,UUID customerId,String fullName,String email,String phone,
        Instant registeredAt,Instant checkedAt,Instant emailAccountApprovedAt,String accountStatus,String identityStatus,boolean emailVerified,
        String reviewState,List<Check> checks,List<String> fundingBlockers,boolean fundingAvailable) {}
    @Transactional(readOnly=true) public Assessment assess(UUID id) {
        return assess(users.findById(id).filter(u->u.getDeletedAt()==null).orElseThrow(()->new NotFoundException("Customer not found")));
    }
    @Transactional(readOnly=true) public Assessment assess(User user) {
        UUID id=user.getId();
        return assessment(user,emails.findById(id).orElse(null),reviews.existsByUserIdAndDecisionAndProviderMode(id,"APPROVED","LIVE"),
            wallets.findByUserId(id),orders.existsByUserIdAndProviderModeNot(id,"LIVE"),orders.existsByUserIdAndProviderMode(id,"LIVE"));
    }
    @Transactional(readOnly=true) public org.springframework.data.domain.Page<Assessment> assessPage(org.springframework.data.domain.Page<User> page) {
        var ids=page.getContent().stream().map(User::getId).toList();
        if(ids.isEmpty()) return page.map(this::assess);
        var emailRows=new HashMap<UUID,EmailChallenge>();emails.findAllById(ids).forEach(e->emailRows.put(e.getUserId(),e));
        var walletRows=new HashMap<UUID,com.savex.entity.Wallet>();wallets.findByUser_IdIn(ids).forEach(w->walletRows.put(w.getUser().getId(),w));
        var liveApprovals=new HashSet<>(reviews.liveApprovedIds(ids));var liveOrders=new HashSet<UUID>();var otherOrders=new HashSet<UUID>();
        orders.modesForUsers(ids).forEach(o->{if("LIVE".equals(o.getProviderMode()))liveOrders.add(o.getUserId());else otherOrders.add(o.getUserId());});
        return page.map(u->assessment(u,emailRows.get(u.getId()),liveApprovals.contains(u.getId()),Optional.ofNullable(walletRows.get(u.getId())),otherOrders.contains(u.getId()),liveOrders.contains(u.getId())));
    }
    private Assessment assessment(User user,EmailChallenge email,boolean liveReview,Optional<com.savex.entity.Wallet> wallet,boolean hasOtherOrders,boolean hasLiveOrders) {
        UUID id=user.getId();
        boolean emailVerified=user.isEmailVerified() && email!=null && email.getVerifiedAt()!=null && user.getEmail().equals(email.getEmail());
        boolean nameValid=user.getFullName()!=null && user.getFullName().trim().length()>=2 && user.getFullName().length()<=100;
        boolean emailValid=user.getEmail()!=null && user.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
        boolean phoneValid=user.getPhone()!=null && user.getPhone().matches("\\+234[789][01][0-9]{8}");
        boolean identityReviewed=user.getKycLevel()!=KycStatus.UNVERIFIED;
        boolean allowed=settings.getLiveAllowedUsers().contains(id);
        boolean clean=wallet.isPresent() && !hasOtherOrders
            && (hasLiveOrders || (wallet.get().getBalanceKobo()==0 && wallet.get().getLockedKobo()==0));
        var checks=List.of(new Check("Name format",nameValid,"Checks format only, not identity ownership"),
            new Check("Email format",emailValid,"Checks email address format"),
            new Check("Phone format",phoneValid,"Checks Nigerian number format; phone ownership is not verified"),
            new Check("Email ownership",emailVerified,emailVerified?"Current email passed SavEx's code check":"Complete the email code check"),
            new Check("Email-only account approval",user.getEmailAccountApprovedAt()!=null,user.getEmailAccountApprovedAt()!=null?
                "Automatically approved after email ownership and account-format checks; identity was not verified":"No automatic account approval recorded"),
            new Check("Identity evidence",identityReviewed,identityReviewed?"A recorded identity approval exists":"Genuine evidence has not been approved"),
            new Check("LIVE review",liveReview,liveReview?"Administrator LIVE approval recorded":"LIVE administrator review is pending"));
        List<String> blockers=new ArrayList<>();
        if(settings.getMode()==MoneySettings.Mode.OFF) blockers.add("Payment service is OFF");
        if(!user.isActive()) blockers.add("Complete email verification and valid account details; restricted accounts cannot be approved automatically");
        if(settings.getMode()==MoneySettings.Mode.LIVE) {
            if(!emailVerified) blockers.add("Verify your current email");
            if(!identityReviewed || !liveReview) blockers.add("An administrator must approve genuine identity evidence for LIVE use");
            if(!allowed) blockers.add("Customer is not in the live pilot allowlist");
            if(!clean) blockers.add("A clean wallet with no test payment history is required");
        }
        String state=!emailVerified?"EMAIL_PENDING":!nameValid||!emailValid||!phoneValid?"DATA_NEEDS_ATTENTION":
            !identityReviewed||!liveReview?"READY_FOR_EVIDENCE_REVIEW":"REVIEW_RECORDED";
        UUID reportId=UUID.nameUUIDFromBytes(("savex-account-assessment-v1:"+id).getBytes(StandardCharsets.UTF_8));
        return new Assessment(reportId,id,user.getFullName(),user.getEmail(),user.getPhone(),user.getCreatedAt(),Instant.now(),
            user.getEmailAccountApprovedAt(),user.getStatus().name(),user.getKycLevel().name(),emailVerified,state,checks,List.copyOf(blockers),blockers.isEmpty());
    }
}
