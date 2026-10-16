package com.savex.money;
import com.savex.dto.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/identity") @RequiredArgsConstructor
public class IdentityController {
    private final IdentityService identity;
    public record ConsentRequest(@NotBlank @Pattern(regexp="[0-9]{11}") String bvn,
        @NotBlank @Size(max=80) String firstname,@NotBlank @Size(max=80) String lastname,
        @NotBlank @Size(max=72) String password,@AssertTrue boolean consent) {}
    @GetMapping public ApiResponse<IdentityService.View> current(@AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.ok(identity.current(UUID.fromString(principal.getUsername())));
    }
    @PostMapping("/bvn/consent") public ApiResponse<IdentityService.View> consent(@AuthenticationPrincipal UserDetails principal,@Valid @RequestBody ConsentRequest body) {
        return ApiResponse.ok(identity.initiate(UUID.fromString(principal.getUsername()),body.bvn(),body.firstname(),body.lastname(),body.password()));
    }
    @PostMapping("/bvn/refresh") public ApiResponse<IdentityService.View> refresh(@AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.ok(identity.refresh(UUID.fromString(principal.getUsername())));
    }
}
