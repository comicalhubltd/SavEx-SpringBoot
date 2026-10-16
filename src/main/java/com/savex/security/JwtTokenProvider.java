package com.savex.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtTokenProvider(
            @Value("${savex.jwt.secret}") String secret,
            @Value("${savex.jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
            @Value("${savex.jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        return generateAccessToken(userId,email,role,null);
    }
    public String generateAccessToken(UUID userId,String email,String role,UUID sessionId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("sid",sessionId==null?null:sessionId.toString())
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        return generateRefreshToken(userId,null);
    }
    public String generateRefreshToken(UUID userId,UUID sessionId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("sid",sessionId==null?null:sessionId.toString())
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiryMs))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            UUID.fromString(claims.getSubject());
            return "access".equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected invalid or expired JWT");
            return false;
        }
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }
}

