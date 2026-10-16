package com.savex.money;
import com.savex.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.UUID;
@RestController @RequiredArgsConstructor
public class PaymentReceiptController {
    private final PaymentReceiptService receipts;
    @GetMapping("/api/v1/money/orders/{id}/receipt")
    public ApiResponse<PaymentReceiptService.Receipt> receipt(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id) {
        return ApiResponse.ok(receipts.receipt(UUID.fromString(principal.getUsername()),id));
    }
}
