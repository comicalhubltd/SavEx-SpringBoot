package com.savex.verification;
import com.savex.dto.response.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/verification/admin-setup") @RequiredArgsConstructor
public class AdminSetupController {
    private final AdminSetupService setup;
    public record Request(@NotBlank @Size(max=512) String setupToken,@NotBlank @Size(max=72) String password) {}
    @PostMapping public ApiResponse<String> activate(@AuthenticationPrincipal UserDetails user,@Valid @RequestBody Request body) {
        setup.activate(UUID.fromString(user.getUsername()),body.setupToken(),body.password());
        return ApiResponse.ok("Administrator activated. Sign in again to the reviewer portal.");
    }
}
