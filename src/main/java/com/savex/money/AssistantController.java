package com.savex.money;

import com.savex.dto.response.ApiResponse;
import com.savex.exception.*;
import com.savex.repository.WalletRepository;
import com.savex.verification.AccountAssessmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
import static com.savex.money.MoneyOrder.*;

/** Guided, deterministic assistants. These endpoints never create an order or change funds. */
@RestController @RequestMapping("/api/v1/assistants") @RequiredArgsConstructor
public class AssistantController {
    private final MoneySettings settings;
    private final MoneyOrderRepository orders;
    private final WalletRepository wallets;
    private final AccountAssessmentService assessments;
    private final MoneyGateway gateway;
    public record SavingsRequest(@Min(100) @Max(10000000000L) long goalKobo,
        @Min(0) @Max(10000000000L) long savedKobo,@Min(1) @Max(520) int periods,
        @NotNull Frequency frequency) {}
    public enum Frequency { WEEKLY, MONTHLY }
    public record SavingsPlan(long remainingKobo,long contributionKobo,long plannedTotalKobo,int periods,Frequency frequency,String message) {}
    @PostMapping("/savings")
    public ApiResponse<SavingsPlan> savings(@Valid @RequestBody SavingsRequest request) {
        long remaining=Math.max(0,request.goalKobo()-request.savedKobo());
        long contribution=(remaining+request.periods()-1)/request.periods();
        return ApiResponse.ok(new SavingsPlan(remaining,contribution,contribution*request.periods(),request.periods(),request.frequency(),
            remaining==0?"Your entered savings already meet the goal. No money was moved.":
            "A suggested contribution schedule based on the amounts you entered. It does not create a circle, reserve funds or schedule automatic debits."));
    }
    public record Troubleshooting(String reference,Kind kind,Status status,long amountKobo,long totalDebitKobo,
        Instant checkedAt,String explanation,String nextStep) {}
    @GetMapping("/transactions/{reference}")
    public ApiResponse<Troubleshooting> troubleshoot(@AuthenticationPrincipal UserDetails principal,@PathVariable String reference) {
        UUID customer=UUID.fromString(principal.getUsername());
        var order=orders.findByReference(reference).filter(o->customer.equals(o.getUserId()))
            .orElseThrow(()->new NotFoundException("Transaction not found in your account"));
        String explanation=switch(order.getStatus()) {
            case QUEUED -> "SavEx recorded this request. Provider submission has not been confirmed.";
            case SUBMITTING,PENDING -> "The final provider result has not been verified. Pending is not proof that the recipient received money.";
            case SUCCESS -> "SavEx recorded a verified successful provider result. This does not independently confirm bank settlement or recipient receipt.";
            case FAILED -> "SavEx recorded a failed provider result. For withdrawals, the normal failure flow releases the reserved amount and SavEx fee.";
            case REVERSED -> "A reversal has been recorded. Check your balance and contact support if it differs from your expectations.";
            case REVIEW -> "This transaction needs administrator investigation. This assistant cannot approve it or release reserved funds.";
        };
        String next=switch(order.getStatus()) {
            case QUEUED,SUBMITTING,PENDING -> "Refresh payment history. Do not submit a replacement. If it remains pending, give support this reference to check provider access, restrictions and the final result.";
            case SUCCESS -> "View the receipt. If the recipient has not received a withdrawal, ask support to trace this exact reference.";
            case FAILED,REVERSED -> "Refresh your wallet balance. Contact support with this reference if the expected funds are unavailable.";
            case REVIEW -> "Contact support with this reference. Do not repeat the payment while it is under review.";
        };
        return ApiResponse.ok(new Troubleshooting(order.getReference(),order.getKind(),order.getStatus(),order.getAmountKobo(),order.totalDebit(),Instant.now(),explanation,next));
    }
    public record PrepareRequest(@NotNull Kind kind,@Min(100) @Max(100000000) long amountKobo,
        @Size(max=10) String accountNumber,@Size(max=6) String bankCode) {}
    public record Proposal(Kind kind,long amountKobo,long applicationFeeKobo,long totalDebitKobo,
        String accountNumber,String bankCode,String recipientName,Instant preparedAt,String message) {}
    @PostMapping("/payments/prepare")
    public ApiResponse<Proposal> prepare(@AuthenticationPrincipal UserDetails principal,@Valid @RequestBody PrepareRequest request) {
        settings.requireEnabled();
        if(request.kind()!=Kind.DEPOSIT && request.kind()!=Kind.WITHDRAWAL)
            throw new BadRequestException("The payment assistant currently supports wallet funding and bank withdrawals");
        UUID customer=UUID.fromString(principal.getUsername());
        var assessment=assessments.assess(customer);
        if(!assessment.fundingAvailable()) throw new BadRequestException("Your account is not currently eligible. Check account and payment availability in the dashboard.");
        if(request.kind()==Kind.DEPOSIT)settings.requireInline();
        else if(!settings.isTransfersApproved())throw new BadRequestException("Bank withdrawals are awaiting activation");
        long fee=settings.applicationFee(request.amountKobo()),total=Math.addExact(request.amountKobo(),fee);
        if(request.amountKobo()>settings.getMaxTransactionKobo())throw new BadRequestException("Per-transaction limit exceeded");
        if(settings.getMode()==MoneySettings.Mode.LIVE) {
            long used=orders.debits(customer,Instant.now().minus(Duration.ofDays(1)),List.of(request.kind()),List.of(Status.FAILED,Status.REVERSED));
            if(request.amountKobo()>settings.getLiveMaxTransactionKobo() || total>settings.getLiveDailyLimitKobo()-used)
                throw new BadRequestException("Live pilot payment limit exceeded, including fees and pending transactions");
        }
        String recipient=null;
        if(request.kind()==Kind.WITHDRAWAL) {
            if(request.accountNumber()==null || !request.accountNumber().matches("[0-9]{10}")
                || request.bankCode()==null || !request.bankCode().matches("[0-9]{2,6}"))
                throw new BadRequestException("Select a bank and enter a ten-digit account number");
            var wallet=wallets.findByUserId(customer).orElseThrow(()->new NotFoundException("Wallet not found"));
            if(wallet.getAvailableKobo()<total)throw new BadRequestException("Insufficient available funds including the SavEx fee");
            long used=orders.debits(customer,Instant.now().minus(Duration.ofDays(1)),List.of(Kind.WITHDRAWAL,Kind.AIRTIME,Kind.DATA,Kind.ELECTRICITY,Kind.CABLE,Kind.INTERNET,Kind.CONTRIBUTION),List.of(Status.FAILED,Status.REVERSED));
            if(total>settings.getDailyDebitLimitKobo()-used)throw new BadRequestException("Daily outgoing limit exceeded");
            try {
                var bank=gateway.resolveBank(request.accountNumber(),request.bankCode());
                recipient=bank.path("account_name").asText();
                if(recipient.isBlank() || !request.accountNumber().equals(bank.path("account_number").asText()))
                    throw new IllegalStateException("Invalid account resolution");
            } catch(RuntimeException error) {throw new BadRequestException("Recipient could not be confirmed. No payment was submitted.");}
        }
        return ApiResponse.ok(new Proposal(request.kind(),request.amountKobo(),fee,total,
            request.kind()==Kind.WITHDRAWAL?request.accountNumber():null,request.kind()==Kind.WITHDRAWAL?request.bankCode():null,
            recipient,Instant.now(),"Proposal only. Review in Payments and confirm there. Fees, eligibility, recipient and available funds are checked again at submission. No funds have been reserved or sent."));
    }
}
