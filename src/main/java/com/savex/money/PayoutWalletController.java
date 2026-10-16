package com.savex.money;
import com.savex.dto.response.ApiResponse;
import com.savex.exception.*;
import com.savex.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/money/payout-wallet") @RequiredArgsConstructor
public class PayoutWalletController {
 private final PayoutWalletService service;
 private final PayoutWalletRepository wallets;
 private final MoneySettings settings;
 private final FlutterwaveGateway gateway;
 private final MoneyLedger ledger;
 private final WithdrawalPreflight withdrawals;
 private final UserRepository users;
 private final PasswordEncoder passwords;
 public record Authorisation(@NotBlank @Size(max=72) String password) {}
 public record Recovery(@NotBlank @Size(max=72) String password,@NotNull UUID customerId,@Pattern(regexp="PSA[A-Za-z0-9]{1,77}") @NotNull String reference) {}
 private UUID id(UserDetails principal) {return UUID.fromString(principal.getUsername());}
 private void authorise(UUID actor,String password) {
    var user=users.findById(actor).orElseThrow();
    if(!user.isActive() || !passwords.matches(password,user.getPasswordHash()))throw new UnauthorizedException("Confirm your password to authorise this action");
 }
 @GetMapping public ApiResponse<PayoutWalletService.View> view(@AuthenticationPrincipal UserDetails principal) {return ApiResponse.ok(service.view(id(principal)));}
 @PostMapping public ApiResponse<PayoutWalletService.View> create(@AuthenticationPrincipal UserDetails principal,@Valid @RequestBody Authorisation body) {
    UUID user=id(principal);authorise(user,body.password());return ApiResponse.ok(service.create(user));
 }
 @PostMapping("/refresh") public ApiResponse<String> refresh(@AuthenticationPrincipal UserDetails principal) {
    service.refresh(id(principal));return ApiResponse.ok("Funding and balance verification requested");
 }
 @PostMapping("/recover") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<String> recover(@AuthenticationPrincipal UserDetails principal,@Valid @RequestBody Recovery body) {
    authorise(id(principal),body.password());service.recover(id(principal),body.customerId(),body.reference());return ApiResponse.ok("Existing provider wallet recovered; no new wallet was created");
 }
 @PostMapping("/withdrawals") public ApiResponse<MoneyView> withdraw(@AuthenticationPrincipal UserDetails principal,
        @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody MoneyDraft draft) {
    UUID user=id(principal);service.eligible(user);
    if(draft.kind()!=MoneyOrder.Kind.WITHDRAWAL)throw new BadRequestException("Select a bank withdrawal");
    var wallet=wallets.findByUserIdAndProviderMode(user,settings.getMode().name()).orElseThrow(()->new BadRequestException("Create a funding account first"));
    var existing=ledger.existing(user,key,draft);
    if(existing.isPresent()) {
        // The ledger validates the saved funding source before returning an idempotent result.
        return ApiResponse.ok(ledger.createPayoutWithdrawal(user,key,draft,wallet.getAccountReference(),0,0));
    }
    withdrawals.checkRecipient(user,draft);
    long fee,available;
    try {fee=gateway.transferFee(draft.amountKobo());available=gateway.payoutBalance(wallet.getAccountReference());}
    catch(RuntimeException error) {throw new BadRequestException("Funding-account balance or transfer fee could not be confirmed. No withdrawal was created.");}
    return ApiResponse.ok(ledger.createPayoutWithdrawal(user,key,draft,wallet.getAccountReference(),available,fee));
 }
}
