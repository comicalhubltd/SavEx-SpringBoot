package com.savex.money;
import com.fasterxml.jackson.databind.JsonNode;
import com.savex.exception.*;
import com.savex.entity.User;
import com.savex.repository.UserRepository;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class IdentityService {
    private final IdentityRepository checks;
    private final UserRepository users;
    private final FlutterwaveGateway gateway;
    private final MoneySettings settings;
    private final PasswordEncoder passwords;
    private final TransactionTemplate tx;
    @PersistenceContext private EntityManager em;
    public record View(String bvnStatus,String ninStatus,String consentUrl) {}
    private View view(IdentityCheck c) {return new View(c==null?"NOT_STARTED":c.getStatus(),"AWAITING_PROVIDER_API",c==null?null:c.getConsentUrl());}
    public View current(UUID user) {
        if(!settings.isIdentityEnabled()) return new View("PAUSED","AWAITING_PROVIDER_API",null);
        return view(checks.findByUserId(user).orElse(null));
    }
    public View initiate(UUID userId,String bvn,String firstname,String lastname,String password) {
        settings.requireIdentity();
        User user=users.findById(userId).orElseThrow();
        if((!user.isActive() && user.getStatus()!=com.savex.entity.enums.UserStatus.PENDING_VERIFICATION)
            || user.getDeletedAt()!=null || password==null || !passwords.matches(password,user.getPasswordHash()))
            throw new BadRequestException("Confirm your password to start verification");
        IdentityCheck check=tx.execute(status->{
            em.find(User.class,userId,LockModeType.PESSIMISTIC_WRITE);
            var old=checks.findByUserId(userId);
            if(old.isPresent() && !old.get().getStatus().equals("NOT_STARTED"))
                throw new BadRequestException("A verification request already exists. Resume it or contact support.");
            IdentityCheck c=old.orElseGet(IdentityCheck::new);c.setUserId(userId);c.setStatus("STARTING");c.setUpdatedAt(Instant.now());
            c.setProviderMode(settings.getIdentityMode().name());
            return checks.saveAndFlush(c);
        });
        try {
            JsonNode data=gateway.initiateBvn(bvn,firstname,lastname);
            String reference=data.path("reference").asText(),url=data.path("redirect_url").asText(null);
            if(url==null) url=data.path("url").asText(null);
            if(reference.isBlank() || reference.length()>120) throw new IllegalStateException("Missing verification reference");
            if(Objects.equals(url,settings.getPublicUrl()+"/?identity=returned")) url=null;
            if(url!=null && !url.isBlank()) {
                java.net.URI target=java.net.URI.create(url);
                if(!"https".equals(target.getScheme()) || !Set.of("nibss-bvn-consent-management.myflutterwave.com",
                    "nibss-bvn-consent-management.dev-flutterwave.com").contains(target.getHost()))
                    throw new IllegalStateException("Unexpected consent URL");
            }
            String consentUrl=url;
            return tx.execute(status->{
                var c=checks.findById(check.getId()).orElseThrow();c.setProviderReference(reference);c.setConsentUrl(consentUrl);c.setStatus("AWAITING_CONSENT");c.setUpdatedAt(Instant.now());
                return view(checks.save(c));
            });
        } catch(RuntimeException e) {
            tx.executeWithoutResult(status->{var c=checks.findById(check.getId()).orElseThrow();c.setStatus("NEEDS_SUPPORT");c.setUpdatedAt(Instant.now());checks.save(c);});
            throw new BadRequestException("Verification could not be confirmed. Contact support before retrying.");
        }
        // Raw BVN and the provider's identity payload are never persisted or logged.
    }
    public View refresh(UUID userId) {
        settings.requireIdentity();
        IdentityCheck c=checks.findByUserId(userId).orElseThrow(()->new BadRequestException("Start BVN verification first"));
        if(!settings.getIdentityMode().name().equals(c.getProviderMode())) throw new BadRequestException("Verification belongs to a different provider environment; contact support");
        if(c.getProviderReference()==null || c.getStatus().equals("EVIDENCE_RECEIVED")) return view(c);
        Boolean allowed=tx.execute(status->{
            em.find(User.class,userId,LockModeType.PESSIMISTIC_WRITE);
            var latest=checks.findById(c.getId()).orElseThrow();
            if(latest.getUpdatedAt().isAfter(Instant.now().minusSeconds(30))) return false;
            latest.setUpdatedAt(Instant.now());checks.save(latest);return true;
        });
        if(!Boolean.TRUE.equals(allowed)) return current(userId);
        JsonNode response=gateway.retrieveBvn(c.getProviderReference());
        String result="AWAITING_CONSENT";
        if(!c.getProviderReference().equals(response.path("reference").asText())) result="NEEDS_SUPPORT";
        else if("COMPLETED".equals(response.path("status").asText()) && response.path("bvn_data").isObject()) result="EVIDENCE_RECEIVED";
        else if(Set.of("FAILED","DECLINED").contains(response.path("status").asText())) result="NEEDS_SUPPORT";
        String finalResult=result;
        return tx.execute(status->{
            var latest=checks.findById(c.getId()).orElseThrow();latest.setStatus(finalResult);
            if(finalResult.equals("EVIDENCE_RECEIVED")) latest.setConsentUrl(null);
            checks.save(latest);return view(latest);
        });
        // BVN evidence alone does not prove NIN, liveness, sanctions clearance, or full KYC.
    }
}
