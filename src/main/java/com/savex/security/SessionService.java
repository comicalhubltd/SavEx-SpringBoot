package com.savex.security;
import com.savex.entity.User;
import com.savex.entity.enums.UserStatus;
import com.savex.repository.UserRepository;
import com.savex.dto.response.*;
import com.savex.exception.UnauthorizedException;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class SessionService {
    private final SessionTokenRepository sessions;
    private final UserRepository users;
    private final JwtTokenProvider jwt;
    @Value("${savex.jwt.access-token-expiry-ms}") private long accessExpiry;
    private String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 unavailable",exception);}
    }
    private Claims refreshClaims(String token) {
        try {
            Claims claims=jwt.parseToken(token);
            if(!"refresh".equals(claims.get("type",String.class)))throw new IllegalArgumentException();
            UUID.fromString(claims.getSubject());UUID.fromString(claims.get("sid",String.class));
            return claims;
        }catch(JwtException|IllegalArgumentException|NullPointerException exception){throw new UnauthorizedException("Your session has ended. Please sign in again.");}
    }
    private boolean available(SessionToken session) {
        return !session.isRevoked() && session.getExpiresAt().isAfter(Instant.now());
    }
    private AuthResponse response(User user,SessionToken session,String refresh) {
        return AuthResponse.of(jwt.generateAccessToken(user.getId(),user.getEmail(),user.getRole().name(),session.getId()),
            refresh,accessExpiry/1000,UserResponse.from(user));
    }
    @Transactional
    public AuthResponse issue(User user) {
        SessionToken session=new SessionToken();session.setUserId(user.getId());
        String refresh=jwt.generateRefreshToken(user.getId(),session.getId());
        session.setTokenHash(hash(refresh));session.setExpiresAt(jwt.parseToken(refresh).getExpiration().toInstant());
        sessions.save(session);return response(user,session,refresh);
    }
    @Transactional
    public AuthResponse refresh(String token) {
        Claims claims=refreshClaims(token);
        var session=sessions.lock(UUID.fromString(claims.get("sid",String.class))).orElseThrow(()->new UnauthorizedException("Session unavailable"));
        if(!available(session) || !session.getUserId().toString().equals(claims.getSubject())
            || !MessageDigest.isEqual(session.getTokenHash().getBytes(StandardCharsets.US_ASCII),hash(token).getBytes(StandardCharsets.US_ASCII)))
            throw new UnauthorizedException("Your session has ended. Please sign in again.");
        User user=users.findById(session.getUserId()).orElseThrow(()->new UnauthorizedException("Account unavailable"));
        if(user.getDeletedAt()!=null || (!user.isActive() && user.getStatus()!=UserStatus.PENDING_VERIFICATION))
            throw new UnauthorizedException("Account restricted. Contact the administrator.");
        // Preserve the original absolute session expiry instead of extending the session indefinitely.
        String next=jwt.generateRefreshToken(user.getId(),session.getId());
        session.setTokenHash(hash(next));return response(user,session,next);
    }
    @Transactional
    public void logout(String token) {
        Claims claims=refreshClaims(token);
        sessions.lock(UUID.fromString(claims.get("sid",String.class))).ifPresent(session->{
            // A signed credential can only revoke its own session, even if a renewal raced with sign-out.
            if(session.getUserId().toString().equals(claims.getSubject()))session.setRevoked(true);
        });
    }
    @Transactional(readOnly=true)
    public boolean activeAccess(String token) {
        Claims claims=jwt.parseToken(token);String id=claims.get("sid",String.class);
        // Previously issued access tokens remain compatible until their short expiry.
        if(id==null)return true;
        return sessions.findById(UUID.fromString(id)).filter(this::available)
            .filter(session->session.getUserId().toString().equals(claims.getSubject())).isPresent();
    }
}
