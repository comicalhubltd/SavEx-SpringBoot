package com.savex.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    public static AuthResponse of(String access, String refresh, long expiresIn, UserResponse user) {
        return new AuthResponse(access, refresh, "Bearer", expiresIn, user);
    }
}
