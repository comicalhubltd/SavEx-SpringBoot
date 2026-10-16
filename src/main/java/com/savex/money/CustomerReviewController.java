package com.savex.money;
import com.savex.dto.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/admin/customer-reviews") @RequiredArgsConstructor
public class CustomerReviewController {
    private final CustomerReviewService reviews;
    private final com.savex.repository.UserRepository users;
    private final CustomerReviewRepository reports;
    private final com.savex.verification.AccountAssessmentService assessments;
    @GetMapping("/assessments") public ApiResponse<org.springframework.data.domain.Page<com.savex.verification.AccountAssessmentService.Assessment>> assessments(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="PENDING_VERIFICATION") String status) {
        if(page<0 || page>10000) throw new com.savex.exception.BadRequestException("Invalid page");
        var pageable=org.springframework.data.domain.PageRequest.of(page,50,org.springframework.data.domain.Sort.by("createdAt").and(org.springframework.data.domain.Sort.by("id")));
        if("ALL".equals(status)) return ApiResponse.ok(assessments.assessPage(users.findAllByDeletedAtIsNull(pageable)));
        try {return ApiResponse.ok(assessments.assessPage(users.findByStatusAndDeletedAtIsNull(com.savex.entity.enums.UserStatus.valueOf(status),pageable)));}
        catch(IllegalArgumentException ex) {throw new com.savex.exception.BadRequestException("Invalid customer status filter");}
    }
    public record Review(@NotNull UUID userId,@NotBlank @Size(max=72) String password,
        @NotBlank @Size(max=200) String evidenceReference,boolean emailOwnershipChecked,
        boolean phoneOwnershipChecked,boolean identityMatched,boolean approve,
        @Size(max=24) String identityMethod,@Size(max=2000) String reviewNotes,@Pattern(regexp="TEST|LIVE") String environment) {}
    public record Candidate(UUID id,String fullName,String email,boolean emailVerified) {}
    @GetMapping("/customers") public ApiResponse<org.springframework.data.domain.Page<Candidate>> customers(@RequestParam(defaultValue="0") int page) {
        if(page<0 || page>10000) throw new com.savex.exception.BadRequestException("Invalid page");
        return ApiResponse.ok(users.findByStatusAndDeletedAtIsNull(com.savex.entity.enums.UserStatus.PENDING_VERIFICATION,
            org.springframework.data.domain.PageRequest.of(page,50,org.springframework.data.domain.Sort.by("createdAt")))
            .map(u->new Candidate(u.getId(),u.getFullName(),u.getEmail(),u.isEmailVerified())));
    }
    @GetMapping public ApiResponse<java.util.List<CustomerReview>> reports() {
        return ApiResponse.ok(reports.findAll(org.springframework.data.domain.PageRequest.of(0,100,org.springframework.data.domain.Sort.by("createdAt").descending())).getContent());
    }
    @PostMapping public ApiResponse<String> review(@AuthenticationPrincipal UserDetails principal,@Valid @RequestBody Review body) {
        if(body.identityMethod()==null) reviews.review(UUID.fromString(principal.getUsername()),body.userId(),body.password(),body.evidenceReference(),
            body.emailOwnershipChecked(),body.phoneOwnershipChecked(),body.identityMatched(),body.approve());
        else reviews.review(UUID.fromString(principal.getUsername()),body.userId(),body.password(),body.evidenceReference(),
            body.emailOwnershipChecked(),body.identityMatched(),body.approve(),body.identityMethod(),body.reviewNotes(),body.environment());
        return ApiResponse.ok("Review recorded");
    }
}
