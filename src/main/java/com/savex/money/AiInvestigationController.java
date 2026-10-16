package com.savex.money;
import com.savex.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/admin/ai") @RequiredArgsConstructor
public class AiInvestigationController {
    private final AiInvestigationService service;
    private final AiSettings settings;
    @GetMapping("/status") public ApiResponse<Map<String,Object>> status(){return ApiResponse.ok(Map.of("enabled",settings.isEnabled()&&!settings.getApiKey().isBlank(),"model",settings.getModel(),"dailyRequestLimit",settings.getDailyLimit(),"learningEnabled",settings.isLearningEnabled(),"scope","Read-only investigation. No live provider lookup, financial actions or model training."));}
    public record Request(@NotBlank @Pattern(regexp="savex-[A-Za-z0-9-]{1,74}") String reference) {}
    public record Rating(@NotNull AiInvestigationService.Feedback rating) {}
    public record Decision(boolean approve) {}
    @PostMapping("/investigate") public ApiResponse<AiInvestigationService.Entry> investigate(@AuthenticationPrincipal UserDetails actor,@Valid @RequestBody Request body){return ApiResponse.ok(service.investigate(UUID.fromString(actor.getUsername()),body.reference()));}
    @GetMapping("/reports") public ApiResponse<List<AiInvestigationService.Entry>> reports(@RequestParam(defaultValue="RUN")String kind){return ApiResponse.ok(service.list(kind));}
    @PostMapping("/runs/{id}/feedback") public ApiResponse<AiInvestigationService.Entry> feedback(@AuthenticationPrincipal UserDetails actor,@PathVariable UUID id,@Valid @RequestBody Rating body){return ApiResponse.ok(service.feedback(UUID.fromString(actor.getUsername()),id,body.rating()));}
    @PostMapping("/lessons/{id}/review") public ApiResponse<AiInvestigationService.Entry> review(@AuthenticationPrincipal UserDetails actor,@PathVariable UUID id,@RequestBody Decision body){return ApiResponse.ok(service.review(UUID.fromString(actor.getUsername()),id,body.approve()));}
    @PostMapping("/daily-review") public ApiResponse<AiInvestigationService.Entry> daily(){return ApiResponse.ok(service.summarize(LocalDate.now(ZoneId.of("Africa/Lagos")).minusDays(1)));}
}
