package com.savex.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String emailOrPhone,
        @NotBlank String password
) {}
