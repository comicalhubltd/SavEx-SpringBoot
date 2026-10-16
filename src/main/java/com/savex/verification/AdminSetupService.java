package com.savex.verification;
import com.savex.entity.User;
import com.savex.entity.enums.*;
import com.savex.repository.UserRepository;
import com.savex.exception.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class AdminSetupService {
    private final String setupToken;
    private final UserRepository users;
    private final EmailChallengeRepository emails;
    private final PasswordEncoder passwords;
    @PersistenceContext private EntityManager em;
    public AdminSetupService(@Value("${savex.admin.setup-token:}") String token,UserRepository users,
                             EmailChallengeRepository emails,PasswordEncoder passwords) {
        setupToken=token;this.users=users;this.emails=emails;this.passwords=passwords;
    }
    @Transactional public void activate(UUID id,String token,String password) {
        if(setupToken.length()<32 || token==null || !MessageDigest.isEqual(setupToken.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))
            throw new UnauthorizedException("Invalid administrator setup token");
        var lock=em.find(AdminSetupLock.class,1,LockModeType.PESSIMISTIC_WRITE);
        if(lock==null) throw new BadRequestException("Administrator setup requires the persistent database migration");
        if(lock.getCompletedAt()!=null || users.existsByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN,UserStatus.ACTIVE))
            throw new ConflictException("First administrator setup has already been completed");
        var user=em.find(User.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(user==null || user.getDeletedAt()!=null || user.getStatus()==UserStatus.SUSPENDED || user.getStatus()==UserStatus.DEACTIVATED)
            throw new BadRequestException("This account cannot become an administrator");
        if(password==null || !passwords.matches(password,user.getPasswordHash())) throw new UnauthorizedException("Confirm your account password");
        var email=emails.findById(id).orElse(null);
        if(!user.isEmailVerified() || email==null || email.getVerifiedAt()==null || !user.getEmail().equals(email.getEmail()))
            throw new BadRequestException("Verify the reviewer's email through SavEx first");
        user.setRole(UserRole.ADMIN);user.setStatus(UserStatus.ACTIVE);
        // Administrator access does not certify identity or grant live payment approval.
        lock.setAdministratorId(id);lock.setCompletedAt(Instant.now());
    }
}
