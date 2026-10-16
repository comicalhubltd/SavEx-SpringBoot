package com.savex.money;
import com.savex.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/admin/reconciliation") @RequiredArgsConstructor
public class ReconciliationController {
    private final ReconciliationService reconciliation;
    private final ReconciliationIssueRepository issues;
    private final FlutterwaveGateway gateway;
    private final WalletAuditService walletAudit;
    private final TransactionHistorySync historySync;
    @GetMapping("/transactions") public ApiResponse<TransactionHistorySync.Report> transactions(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="ALL") String filter) {
        return ApiResponse.ok(historySync.report(page,filter));
    }
    @PostMapping("/transactions/sync") public ApiResponse<TransactionHistorySync.Report> sync() {
        historySync.request();return ApiResponse.ok(historySync.report(0,"ALL"));
    }
    @GetMapping("/wallets") public ApiResponse<WalletAuditService.Report> wallets(@RequestParam(defaultValue="0") int page) {
        return ApiResponse.ok(walletAudit.report(page));
    }
    @GetMapping("/issues") public ApiResponse<List<ReconciliationIssue>> issues() {
        return ApiResponse.ok(issues.findTop100ByStatusOrderByCreatedAtAsc("OPEN"));
    }
    @PostMapping("/settlements/{id}") public ApiResponse<Integer> settlement(@PathVariable String id,@RequestParam(defaultValue="1") int page) {
        return ApiResponse.ok(reconciliation.settlement(id,gateway.settlement(id,page)));
    }
    private final MoneyOrderRepository orders;
    private final MoneySettings settings;
    @PostMapping("/orders/{id}/reverify") public ApiResponse<String> reverify(@PathVariable UUID id) {
        var order=orders.findById(id).orElseThrow(()->new com.savex.exception.NotFoundException("Order not found"));
        if(order.getDebitSubaccount()!=null && order.getKind()==MoneyOrder.Kind.DEPOSIT)
            throw new com.savex.exception.BadRequestException("Payout wallet funding uses the dedicated funding verification worker");
        if(!settings.getMode().name().equals(order.getProviderMode()))
            throw new com.savex.exception.BadRequestException("Order belongs to another provider environment");
        reconciliation.terminal(id,gateway.verify(order));
        return ApiResponse.ok("Provider outcome checked; review exceptions before further payments");
    }
    public record Evidence(@NotBlank @Size(max=72) String password,@NotBlank @Size(max=200) String evidenceReference) {}
    public record BankEvidence(@NotBlank @Size(max=72) String password,@Positive long netKobo,@NotBlank @Size(max=200) String evidenceReference) {}
    @PostMapping("/orders/{id}/bank-receipt") public ApiResponse<String> bankReceipt(@AuthenticationPrincipal UserDetails principal,
        @PathVariable UUID id,@Valid @RequestBody BankEvidence body) {
        reconciliation.bankReceipt(UUID.fromString(principal.getUsername()),id,body.password(),body.netKobo(),body.evidenceReference());
        return ApiResponse.ok("Bank evidence and balancing journal entry recorded");
    }
    @PostMapping("/issues/{id}/acknowledge") public ApiResponse<String> acknowledge(@AuthenticationPrincipal UserDetails principal,
        @PathVariable UUID id,@Valid @RequestBody Evidence body) {
        reconciliation.acknowledge(UUID.fromString(principal.getUsername()),id,body.password(),body.evidenceReference());
        return ApiResponse.ok("Evidence recorded; no balance or account status changed");
    }
}
