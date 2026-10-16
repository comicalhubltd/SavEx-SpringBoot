package com.savex.security;

import com.savex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        var user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

        boolean onboarding=user.getStatus()==com.savex.entity.enums.UserStatus.PENDING_VERIFICATION && user.getDeletedAt()==null;
        return User.builder()
                .username(user.getId().toString())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(onboarding?(user.isEmailVerified()?"ROLE_VERIFIED":"ROLE_ONBOARDING"):"ROLE_" + user.getRole().name())))
                .accountExpired(false)
                .accountLocked(!user.isActive() && !onboarding)
                .credentialsExpired(false)
                .disabled(!user.isActive() && !onboarding)
                .build();
    }
}
