package com.savex.money;
import com.fasterxml.jackson.databind.JsonNode;
import com.savex.exception.*;
import com.savex.repository.UserRepository;
import com.savex.verification.AccountAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class PayoutWalletService {
 private final MoneySettings settings;
 private final PayoutWalletRepository wallets;
 private final PayoutFundingEventRepository events;
 private final PayoutWalletStore store;
 private final FlutterwaveGateway gateway;
 private final UserRepository users;
 private final AccountAssessmentService assessments;
 public record View(boolean enabled,String mode,String state,String accountName,String accountNumber,String bankName,
    long balanceKobo,long reservedKobo,long availableKobo,Long providerAvailableKobo,Instant checkedAt,String note) {}
 public View view(UUID user) {
    var wallet=wallets.findByUserIdAndProviderMode(user,settings.getMode().name());
    return wallet.map(w->new View(settings.isPayoutWalletsEnabled(),w.getProviderMode(),w.getState(),w.getAccountName(),w.getAccountNumber(),w.getBankName(),
        w.getBalanceKobo(),w.getLockedKobo(),w.available(),w.getProviderAvailableKobo(),w.getCheckedAt(),w.getSyncNote())).orElse(
        new View(settings.isPayoutWalletsEnabled(),settings.getMode().name(),"NOT_CREATED",null,null,null,0,0,0,null,null,"Your existing checkout wallet remains separate."));
 }
 public void eligible(UUID user) {
    settings.requirePayoutWallets();var assessment=assessments.assess(user);
    if(!"ACTIVE".equals(assessment.accountStatus()) || !assessment.fundingAvailable())throw new BadRequestException("An active approved customer account is required for a funding account");
 }
 public View create(UUID user) {
    eligible(user);var customer=users.findById(user).orElseThrow();var creation=store.begin(user,settings.getMode().name());var wallet=creation.wallet();
    if(creation.create()) {
        try {store.bind(wallet.getId(),gateway.createPayoutWallet(customer.getFullName(),customer.getEmail()),customer.getEmail());}
        catch(RuntimeException error) {store.uncertain(wallet.getId());throw new BadRequestException("Wallet creation could not be confirmed. Contact support to recover it; do not create another wallet.");}
    }
    wallet=wallets.findById(wallet.getId()).orElseThrow();
    if(wallet.getAccountReference()==null)throw new ConflictException("Wallet creation is pending or uncertain. Support must recover the existing wallet before retrying.");
    if(wallet.getAccountNumber()==null) {
        try {store.account(wallet.getId(),gateway.payoutAccount(wallet.getAccountReference()));}
        catch(RuntimeException error) {throw new BadRequestException("Wallet saved. Funding account details are not available yet; retry retrieving the account later.");}
    }
    return view(user);
 }
 public void recover(UUID reviewer,UUID user,String reference) {
    eligible(user);var customer=users.findById(user).orElseThrow();var found=wallets.findByUserIdAndProviderMode(user,settings.getMode().name()).orElseThrow();
    try {
        var remote=gateway.payoutWallet(reference);
        if(!reference.equals(remote.path("account_reference").asText()))throw new BadRequestException("Provider wallet reference mismatch");
        store.recover(found.getId(),reviewer,remote,customer.getEmail(),gateway.payoutAccount(reference));
    }catch(BadRequestException|ConflictException error){throw error;}
    catch(RuntimeException error){throw new BadRequestException("Provider wallet recovery could not be confirmed. Check the reference and ask Flutterwave support to verify access.");}
 }
 public void refresh(UUID user) {settings.requirePayoutWallets();store.requestSync(user,settings.getMode().name());}
 public void notification(JsonNode payload) {
    var data=payload.path("data");
    if(!"transfer.completed".equals(payload.path("event").asText()) || !"PSA".equals(data.path("debit_currency").asText())
        || !"WALLET FUNDING".equals(data.path("narration").asText()))return;
    enqueue(data.path("id").asText());
 }
 private void enqueue(String id) {
    if(!id.matches("[0-9]{1,20}"))return;
    try {store.enqueue(settings.getMode().name(),id);}catch(org.springframework.dao.DataIntegrityViolationException duplicate) { /* Concurrent duplicate notification. */ }
 }
 @Scheduled(fixedDelayString="${savex.money.payout-sync-delay-ms:60000}",initialDelay=30000)
 public void poll() {
    if(settings.getMode()==MoneySettings.Mode.OFF || !settings.isWorkerEnabled())return;
    for(var wallet:wallets.due(settings.getMode().name(),Instant.now().minusSeconds(300),PageRequest.of(0,10))) {
        try {
            var from=wallet.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();var to=LocalDate.now(ZoneOffset.UTC);
            boolean complete=false;
            for(int page=1;page<=10;page++) {
                var rows=gateway.payoutTransactions(wallet.getAccountReference(),from,to,page);
                for(var row:rows)if("PSA".equals(row.path("debit_currency").asText()) && "WALLET FUNDING".equals(row.path("narration").asText()))enqueue(row.path("id").asText());
                if(rows.size()<100) {complete=true;break;}
            }
            store.snapshot(wallet.getId(),gateway.payoutBalance(wallet.getAccountReference()),complete?"Provider balance checked. Funding is credited only after individual transfer verification.":"History scan incomplete; support should review older transactions.",complete);
        }catch(RuntimeException error) {store.syncError(wallet.getId());}
    }
    for(String state:List.of("PENDING","CONFIRMED"))for(var event:events.findByProviderModeAndStateAndNextCheckAtBeforeOrderByCreatedAtAsc(settings.getMode().name(),state,Instant.now(),PageRequest.of(0,20))) {
        if(!store.claimEvent(event.getId()))continue;
        try {store.funding(event.getId(),gateway.payoutFunding(event.getTransferId()));}catch(RuntimeException error) { /* Durable event retries; no credit based on callback alone. */ }
    }
 }
}
