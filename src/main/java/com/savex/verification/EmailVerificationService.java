package com.savex.verification;

import com.savex.entity.User;
import com.savex.entity.enums.UserStatus;
import com.savex.exception.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class EmailVerificationService {
    private final EmailChallengeRepository challenges;
    private final VerificationEmailSender sender;
    private final String secret;
    private final AccountApprovalService approvals;
    private final SecureRandom random=new SecureRandom();
    @PersistenceContext private EntityManager em;
    public EmailVerificationService(EmailChallengeRepository challenges,VerificationEmailSender sender,
                                    @Value("${savex.jwt.secret}") String secret,AccountApprovalService approvals) {
        this.challenges=challenges;this.sender=sender;this.secret=secret;this.approvals=approvals;
    }
    public record State(boolean verified,Instant expiresAt,Instant nextSendAt,String message) {}
    private User customer(UUID id) {
        User user=em.find(User.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(user==null || user.getDeletedAt()!=null) throw new NotFoundException("Customer not found");
        if(user.getStatus()==UserStatus.SUSPENDED || user.getStatus()==UserStatus.DEACTIVATED)
            throw new BadRequestException("This account cannot request verification");
        return user;
    }
    private boolean verified(User user,EmailChallenge challenge) {
        return challenge!=null && challenge.getVerifiedAt()!=null && user.isEmailVerified()
            && user.getEmail().equals(challenge.getEmail());
    }
    private State state(User user,EmailChallenge c,String message) {
        Instant next=c==null || c.getSentAt()==null?null:c.getSentAt().plusSeconds(60);
        if(c!=null && c.getSendCount()>=5 && c.getWindowStartedAt()!=null) {
            Instant window=c.getWindowStartedAt().plusSeconds(3600);
            if(next==null || window.isAfter(next)) next=window;
        }
        return new State(verified(user,c),c==null?null:c.getExpiresAt(),next,message);
    }
    @Transactional public State status(UUID id) {
        User user=customer(id);var c=challenges.findById(id).orElse(null);approvals.approve(user,c);
        return state(user,c,"Email verification status");
    }
    @Transactional public State send(UUID id) {
        User user=customer(id);Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var c=challenges.findById(id).orElseGet(EmailChallenge::new);
        if(verified(user,c)) {approvals.approve(user,c);return state(user,c,"Email already verified");}
        if(c.getSentAt()!=null && now.isBefore(c.getSentAt().plusSeconds(60)))
            throw new BadRequestException("Wait 60 seconds between verification emails");
        if(c.getWindowStartedAt()==null || !now.isBefore(c.getWindowStartedAt().plusSeconds(3600))) {
            c.setWindowStartedAt(now);c.setSendCount(0);
        }
        if(c.getSendCount()>=5) throw new BadRequestException("Maximum 5 verification emails per hour. Try later.");
        String code=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
        c.setUserId(id);c.setEmail(user.getEmail());c.setSentAt(now);c.setExpiresAt(now.plusSeconds(600));
        c.setNonce(UUID.randomUUID().toString());
        c.setCodeHash(hash(c,code));c.setAttempts(0);c.setVerifiedAt(null);c.setSendCount(c.getSendCount()+1);
        challenges.save(c);
        sender.send(user.getEmail(),code);
        return state(user,c,"Code sent. It expires in 10 minutes.");
    }
    @Transactional public State check(UUID id,String code) {
        User user=customer(id);var c=challenges.findById(id).orElse(null);Instant now=Instant.now();
        // Returning a failure rather than throwing keeps the failed-attempt counter committed.
        if(verified(user,c)) {approvals.approve(user,c);return state(user,c,"Email already verified");}
        if(c==null || c.getCodeHash()==null || c.getNonce()==null || !user.getEmail().equals(c.getEmail())
            || c.getExpiresAt()==null || !now.isBefore(c.getExpiresAt()) || c.getAttempts()>=5)
            return state(user,c,"Code unavailable or expired. Request a new code.");
        c.setAttempts(c.getAttempts()+1);
        if(code==null || !code.matches("[0-9]{6}") || !MessageDigest.isEqual(
            c.getCodeHash().getBytes(StandardCharsets.UTF_8),hash(c,code).getBytes(StandardCharsets.UTF_8))) {
            if(c.getAttempts()>=5) c.setCodeHash(null);
            return state(user,c,"Incorrect code. Five failed attempts invalidate a code.");
        }
        c.setVerifiedAt(now);c.setCodeHash(null);user.setEmailVerified(true);
        approvals.approve(user,c);
        return state(user,c,user.isActive()?"Email verified. Account approved automatically; identity remains unverified until administrator review."
            :"Email verified. Account details need attention before automatic approval.");
    }
    private String hash(EmailChallenge c,String code) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((c.getUserId()+"|"+c.getEmail()+"|"+c.getNonce()+"|"+code).getBytes(StandardCharsets.UTF_8)));
        } catch(GeneralSecurityException ex) { throw new IllegalStateException("Verification hashing unavailable"); }
    }
}
