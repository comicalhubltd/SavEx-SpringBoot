package com.savex.money;
import com.fasterxml.jackson.databind.JsonNode;
import com.savex.dto.response.ApiResponse;
import com.savex.exception.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@RestController @RequestMapping("/api/v1/money") @RequiredArgsConstructor
public class MoneyController {
    private final MoneySettings settings;
    private final MoneyLedger ledger;
    private final GroupMoneyService groups;
    private final MoneyGateway gateway;
    private final BillService bills;
    private final ReconciliationService reconciliation;
    private final com.savex.verification.AccountAssessmentService assessments;
    private final InlineCheckoutService inline;
    private final WithdrawalPreflight withdrawals;
    @PostMapping("/orders/{id}/checkout")
    public ApiResponse<InlineCheckoutService.Checkout> checkout(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id) {
        return ApiResponse.ok(inline.prepare(UUID.fromString(principal.getUsername()),id));
    }
    @PostMapping("/orders/{id}/checkout/check")
    public ApiResponse<String> checkoutCheck(@AuthenticationPrincipal UserDetails principal,@PathVariable UUID id) {
        inline.check(UUID.fromString(principal.getUsername()),id);
        return ApiResponse.ok("Payment verification requested");
    }
    public record FeeQuote(long amountKobo,long applicationFeeKobo,long totalDebitKobo,String currency) {}
    @PostMapping("/quote")
    public ApiResponse<FeeQuote> quote(@Valid @RequestBody MoneyDraft draft) {
        settings.requireEnabled();
        long fee=settings.isFeesEnabled() && draft.kind()!=MoneyOrder.Kind.CONTRIBUTION
            && draft.kind()!=MoneyOrder.Kind.PAYOUT?settings.applicationFee(draft.amountKobo()):0;
        return ApiResponse.ok(new FeeQuote(draft.amountKobo(),fee,Math.addExact(draft.amountKobo(),fee),"NGN"));
    }
    @GetMapping("/capabilities")
    public ApiResponse<Map<String,Object>> capabilities(@AuthenticationPrincipal UserDetails principal) {
        var assessment=assessments.assess(UUID.fromString(principal.getUsername()));
        boolean active="ACTIVE".equals(assessment.accountStatus());
        var blockers=new ArrayList<>(assessment.fundingBlockers());
        if(settings.getMode()!=MoneySettings.Mode.OFF && !settings.inlineConfigured()) blockers.add("Configure FLW_PUBLIC_KEY to enable payment inside SavEx");
        var withdrawalBlockers=new ArrayList<String>();
        if(settings.getMode()==MoneySettings.Mode.LIVE) withdrawalBlockers.addAll(assessment.fundingBlockers());
        else if(!active) withdrawalBlockers.add("This account cannot make withdrawals");
        if(settings.getMode()==MoneySettings.Mode.OFF) withdrawalBlockers.add("Payments are OFF");
        if(!settings.isTransfersApproved()) withdrawalBlockers.add("Bank transfers are awaiting provider activation");
        var billBlockers=new ArrayList<String>();
        if(settings.getMode()==MoneySettings.Mode.LIVE)billBlockers.addAll(assessment.fundingBlockers());
        else if(!active)billBlockers.add("This account cannot pay bills");
        if(settings.getMode()==MoneySettings.Mode.OFF)billBlockers.add("Payments are OFF");
        if(!settings.isBillsApproved())billBlockers.add("Bill payments are awaiting provider activation");
        return ApiResponse.ok(Map.of("mode",settings.getMode(),"currency","NGN","country","NG",
            "collections",assessment.fundingAvailable() && settings.inlineConfigured(),"fundingBlockers",blockers,
            "transfers",withdrawalBlockers.isEmpty(),"withdrawalBlockers",withdrawalBlockers,
            "bills",billBlockers.isEmpty(),"billBlockers",billBlockers));
    }
    @GetMapping("/orders")
    public ApiResponse<List<MoneyView>> history(@AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.ok(ledger.history(UUID.fromString(principal.getUsername())));
    }
    @PostMapping("/orders")
    public ApiResponse<MoneyView> create(@AuthenticationPrincipal UserDetails principal,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody MoneyDraft draft) {
        settings.requireEnabled();
        if(draft.kind()==MoneyOrder.Kind.DEPOSIT) settings.requireInline();
        var existing=ledger.existing(UUID.fromString(principal.getUsername()),key,draft);
        if(existing.isPresent()) return ApiResponse.ok(existing.get());
        if(draft.kind()==MoneyOrder.Kind.WITHDRAWAL) withdrawals.check(UUID.fromString(principal.getUsername()),draft);
        if(draft.kind()==MoneyOrder.Kind.CONTRIBUTION) return ApiResponse.ok(groups.contribute(UUID.fromString(principal.getUsername()),key,draft));
        if(BillService.category(draft.kind())!=null) billPreflight.check(UUID.fromString(principal.getUsername()),draft);
        return ApiResponse.ok(ledger.create(UUID.fromString(principal.getUsername()),key,draft));
    }
    private final BillPreflight billPreflight;
    private final PayoutWalletService payoutWallets;
    @PostMapping("/validate-bill")
    public ApiResponse<BillService.Quote> validateBill(@Valid @RequestBody MoneyDraft draft) {
        settings.requireBills(); return ApiResponse.ok(bills.validate(draft));
    }
    @GetMapping("/billers")
    public ApiResponse<JsonNode> billers(@RequestParam String category) { return ApiResponse.ok(gateway.billers(category)); }
    @GetMapping("/items")
    public ApiResponse<JsonNode> items(@RequestParam String biller) { return ApiResponse.ok(gateway.items(biller)); }
    @GetMapping("/banks")
    public ApiResponse<JsonNode> banks() { return ApiResponse.ok(gateway.banks()); }
    public record BankRequest(String accountNumber,String bankCode) {}
    @PostMapping("/resolve-bank")
    public ApiResponse<JsonNode> resolve(@RequestBody BankRequest body) {
        return ApiResponse.ok(gateway.resolveBank(body.accountNumber(),body.bankCode()));
    }
    @PostMapping("/webhook/flutterwave")
    public void webhook(@RequestHeader(value="verif-hash",required=false) String hash,@RequestBody JsonNode payload) {
        if(settings.getMode()==MoneySettings.Mode.OFF || hash==null || settings.getWebhookSecret().isBlank() ||
           !MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8),settings.getWebhookSecret().getBytes(StandardCharsets.UTF_8)))
            throw new UnauthorizedException("Invalid payment notification");
        JsonNode data=payload.path("data");
        payoutWallets.notification(payload);
        // Callback content is only a wake-up signal. Amounts and statuses are fetched from Flutterwave.
        for(String field:List.of("tx_ref","reference","customer_reference")) {
            String ref=data.path(field).asText();
            if(ref.startsWith("savex-") && ref.length()<=80) {
                String event=payload.path("event").asText().toLowerCase(Locale.ROOT);
                if(event.contains("refund") || event.contains("chargeback") || event.contains("reversal")) reconciliation.dispute(ref);
                else ledger.notifyReference(ref);
            }
        }
    }
}
