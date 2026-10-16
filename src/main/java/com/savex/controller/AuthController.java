package com.savex.controller;

import com.savex.dto.request.LoginRequest;
import com.savex.dto.request.RegisterRequest;
import com.savex.dto.response.ApiResponse;
import com.savex.dto.response.AuthResponse;
import com.savex.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final com.savex.security.SessionService sessions;
    public record RefreshRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=4096) String refreshToken) {}
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(sessions.refresh(request.refreshToken()));
    }
    @PostMapping("/logout")
    public ApiResponse<String> logout(@Valid @RequestBody RefreshRequest request) {
        sessions.logout(request.refreshToken());return ApiResponse.ok("Session signed out");
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("Registration successful", authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email/phone + password")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Login successful", authService.login(request));
    }
}
