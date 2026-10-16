package com.savex.verification;
import com.savex.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/verification/account-assessment") @RequiredArgsConstructor
public class AccountAssessmentController {
    private final AccountAssessmentService assessments;
    @GetMapping public ApiResponse<AccountAssessmentService.Assessment> current(@AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.ok(assessments.assess(UUID.fromString(principal.getUsername())));
    }
}
