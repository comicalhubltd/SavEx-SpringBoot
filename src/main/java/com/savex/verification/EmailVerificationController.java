package com.savex.verification;
import com.savex.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/verification/email") @RequiredArgsConstructor
public class EmailVerificationController {
    private final EmailVerificationService service;
    public record Code(String code) {}
    @GetMapping public ApiResponse<EmailVerificationService.State> status(@AuthenticationPrincipal UserDetails user) {
        return ApiResponse.ok(service.status(UUID.fromString(user.getUsername())));
    }
    @PostMapping("/send") public ApiResponse<EmailVerificationService.State> send(@AuthenticationPrincipal UserDetails user) {
        return ApiResponse.ok(service.send(UUID.fromString(user.getUsername())));
    }
    @PostMapping("/check") public ApiResponse<EmailVerificationService.State> check(@AuthenticationPrincipal UserDetails user,@RequestBody Code code) {
        return ApiResponse.ok(service.check(UUID.fromString(user.getUsername()),code.code()));
    }
}
