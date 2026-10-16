package com.savex.money;
import com.savex.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class TransferBalanceController {
    private final TransferBalanceService balance;
    @GetMapping("/api/v1/admin/payments/transfer-balance") public ApiResponse<TransferBalanceService.Snapshot> current() {
        return ApiResponse.ok(balance.current());
    }
    @GetMapping("/api/v1/money/transfer-balance") public ApiResponse<TransferBalanceService.CustomerStatus> customer() {
        return ApiResponse.ok(balance.customerStatus());
    }
}
