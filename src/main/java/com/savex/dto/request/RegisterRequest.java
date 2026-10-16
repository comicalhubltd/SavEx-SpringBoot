package com.savex.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^(\\+234|0)[789][01]\\d{8}$", message = "Enter a valid Nigerian phone number") String phone,
        @NotBlank @Size(min = 8, max = 72) String password
) {}
