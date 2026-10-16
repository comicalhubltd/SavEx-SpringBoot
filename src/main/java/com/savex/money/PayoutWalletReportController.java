package com.savex.money;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.savex.dto.response.ApiResponse;
@RestController @RequestMapping("/api/v1/admin/payout-wallets") @RequiredArgsConstructor
public class PayoutWalletReportController {
 private final PayoutWalletReport reports;
 @GetMapping public ApiResponse<PayoutWalletReport.Report> report(@RequestParam(defaultValue="0") int page) {return ApiResponse.ok(reports.report(page));}
}
