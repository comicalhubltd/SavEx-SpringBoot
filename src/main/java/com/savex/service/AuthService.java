package com.savex.service;

import com.savex.dto.request.LoginRequest;
import com.savex.dto.request.RegisterRequest;
import com.savex.dto.response.AuthResponse;
import com.savex.entity.User;
import com.savex.entity.Wallet;
import com.savex.exception.ConflictException;
import com.savex.exception.UnauthorizedException;
import com.savex.repository.UserRepository;
import com.savex.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.core.env.Environment environment;
    private final com.savex.verification.AccountApprovalService accountApprovals;
    private final com.savex.security.SessionService sessions;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        String phone = normalizePhone(request.phone().trim());
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new ConflictException("Email already registered");
        }
        if (userRepository.existsByPhoneAndDeletedAtIsNull(phone)) {
            throw new ConflictException("Phone number already registered");
        }

        User user = User.builder()
                .fullName(request.fullName())
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        // Only disposable local development accounts skip the unfinished verification workflow.
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dev & !prod"))) {
            user.setStatus(com.savex.entity.enums.UserStatus.ACTIVE);
        }
        user = userRepository.save(user);

        walletRepository.save(Wallet.builder().user(user).build());

        log.info("New user registered: {}", user.getId());

        return sessions.issue(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailOrPhone(normalizePhone(request.emailOrPhone().trim().toLowerCase(java.util.Locale.ROOT)))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        user = accountApprovals.approveExisting(user.getId());

        if (!user.isActive() && !(user.getStatus()==com.savex.entity.enums.UserStatus.PENDING_VERIFICATION && user.getDeletedAt()==null)) {
            throw new UnauthorizedException(user.getStatus()==com.savex.entity.enums.UserStatus.SUSPENDED
                ? "This account is suspended. Ask the administrator to check account restrictions and payment reconciliation reports."
                : "This account is closed or unavailable. Contact the administrator.");
        }

        return sessions.issue(user);
    }

    private String normalizePhone(String phone) {
        if (phone.matches("0[0-9]{10}")) {
            return "+234" + phone.substring(1);
        }
        return phone;
    }
}
