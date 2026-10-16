package com.savex.controller;

import com.savex.dto.response.ApiResponse;
import com.savex.entity.Wallet;
import com.savex.service.PaymentService;
import com.savex.money.MoneyView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Wallet")
public class WalletController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "Get wallet balance")
    public ApiResponse<Map<String, Object>> getWallet(@AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        Wallet w = paymentService.getWallet(userId);
        return ApiResponse.ok(Map.of(
                "balanceNaira", w.getBalanceNaira(),
                "availableNaira", w.getAvailableNaira(),
                "lockedNaira", w.getLockedKobo() / 100.0
        ));
    }

    @PostMapping("/fund")
    @Operation(summary = "Queue Flutterwave wallet funding; retrieve checkout from /api/v1/money/orders")
    public ApiResponse<MoneyView> fund(
            @AuthenticationPrincipal UserDetails principal,
            @RequestHeader("Idempotency-Key") String key,
            @RequestParam long amountNaira) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ApiResponse.ok("Funding queued; check payment history for secure checkout", paymentService.initiateWalletFunding(userId, amountNaira,key));
    }

    @PostMapping("/webhook/paystack")
    @Operation(summary = "Paystack payment webhook (internal)")
    public void paystackWebhook(
            @RequestHeader("x-paystack-signature") String signature,
            @RequestBody Map<String, Object> payload) {
        String event = (String) payload.get("event");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) payload.get("data");
        paymentService.handlePaystackWebhook(event, data.get("reference"));
    }
}
