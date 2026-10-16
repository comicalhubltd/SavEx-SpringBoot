package com.savex.dto.response;

import com.savex.entity.User;
import com.savex.entity.enums.KycStatus;
import com.savex.entity.enums.UserStatus;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String profilePhoto,
        UserStatus status,
        KycStatus kycLevel,
        boolean emailVerified,
        boolean phoneVerified
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getProfilePhoto(),
                user.getStatus(),
                user.getKycLevel(),
                user.isEmailVerified(),
                user.isPhoneVerified()
        );
    }
}
