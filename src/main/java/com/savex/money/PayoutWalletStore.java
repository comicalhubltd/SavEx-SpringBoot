package com.savex.money;
import com.fasterxml.jackson.databind.JsonNode;
import com.savex.entity.User;
import com.savex.exception.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
/** Short database transactions only; provider requests happen in PayoutWalletService. */
@Service @RequiredArgsConstructor
public class PayoutWalletStore {
 private final PayoutWalletRepository wallets;
 private final PayoutFundingEventRepository events;
 private final MoneyOrderRepository orders;
 private final JournalRepository journal;
 private final MoneySettings settings;
 @PersistenceContext private EntityManager em;
 public record Creation(PayoutWallet wallet,boolean create) {}
 @Transactional public Creation begin(UUID userId,String mode) {
    User user=em.find(User.class,userId,LockModeType.PESSIMISTIC_WRITE);
    if(user==null || !user.isActive())throw new BadRequestException("An active customer account is required");
    var existing=wallets.findByUserIdAndProviderMode(userId,mode);
    if(existing.isPresent())return new Creation(existing.get(),false);
    var wallet=new PayoutWallet();wallet.setUserId(userId);wallet.setProviderMode(mode);wallet.setAccountName(user.getFullName());
    return new Creation(wallets.saveAndFlush(wallet),true);
 }
 @Transactional public void bind(UUID id,JsonNode data,String expectedEmail) {
    var wallet=wallets.lock(id).orElseThrow();
    String ref=data.path("account_reference").asText(),barter=data.path("barter_id").asText(),name=data.path("account_name").asText();
    if(!ref.matches("PSA[A-Za-z0-9]{1,77}") || !barter.matches("[0-9]{1,80}")
       || !expectedEmail.equalsIgnoreCase(data.path("email").asText()) || !"NG".equals(data.path("country").asText())
       || !"ACTIVE".equalsIgnoreCase(data.path("status").asText()) || name.isBlank() || name.length()>100)throw new BadRequestException("Provider wallet ownership or active status could not be confirmed");
    if(wallet.getAccountReference()!=null && !wallet.getAccountReference().equals(ref))throw new ConflictException("Wallet already linked to another provider account");
    wallet.setAccountReference(ref);wallet.setBarterId(barter);wallet.setAccountName(name);wallet.setState("ACCOUNT_PENDING");
 }
 @Transactional public void account(UUID id,JsonNode row) {
    var wallet=wallets.lock(id).orElseThrow();
    String number=row.path("static_account").asText(),bank=row.path("bank_code").asText(),name=row.path("bank_name").asText();
    if(!"NGN".equals(row.path("currency").asText()) || !number.matches("[0-9]{10}") || !bank.matches("[0-9]{2,6}") || name.isBlank() || name.length()>120)
        throw new BadRequestException("Provider funding account details could not be confirmed");
    if(wallet.getAccountNumber()!=null && !wallet.getAccountNumber().equals(number))throw new ConflictException("Funding account changed; contact support");
    wallet.setAccountNumber(number);wallet.setBankCode(bank);wallet.setBankName(name);wallet.setState("ACTIVE");wallet.setSyncRequested(true);
 }
 @Transactional public void recover(UUID id,UUID reviewer,JsonNode remote,String email,JsonNode account) {
    var wallet=wallets.lock(id).orElseThrow();
    if(!Set.of("CREATING","CREATION_UNCERTAIN","ACCOUNT_PENDING").contains(wallet.getState()))throw new ConflictException("This wallet does not need creation recovery");
    bind(id,remote,email);account(id,account);
    em.persist(new PayoutWalletRecovery(id,reviewer,remote.path("account_reference").asText()));
 }
 @Transactional public void uncertain(UUID id) {
    var wallet=wallets.lock(id).orElseThrow();
    if(wallet.getAccountReference()==null)wallet.setState("CREATION_UNCERTAIN");
 }
 @Transactional public void requestSync(UUID user,String mode) {
    var found=wallets.findByUserIdAndProviderMode(user,mode).orElseThrow(()->new BadRequestException("Create a funding account first"));
    wallets.lock(found.getId()).orElseThrow().setSyncRequested(true);
 }
 @Transactional public void snapshot(UUID id,long available,String note,boolean finished) {
    var wallet=wallets.lock(id).orElseThrow();wallet.setProviderAvailableKobo(available);wallet.setCheckedAt(Instant.now());
    wallet.setSyncNote(note);wallet.setSyncRequested(!finished);
 }
 @Transactional public void syncError(UUID id) {
    wallets.lock(id).ifPresent(wallet->{wallet.setSyncNote("Provider check incomplete. Existing balances are retained; retry later.");wallet.setProviderAvailableKobo(null);wallet.setCheckedAt(Instant.now());wallet.setSyncRequested(false);});
 }
 @Transactional public void enqueue(String mode,String transfer) {
    if(events.existsByProviderModeAndTransferId(mode,transfer))return;
    var event=new PayoutFundingEvent();event.setProviderMode(mode);event.setTransferId(transfer);events.saveAndFlush(event);
 }
 @Transactional public boolean claimEvent(UUID id) {
    var event=events.lock(id).orElseThrow();
    if(!Set.of("PENDING","CONFIRMED").contains(event.getState()) || event.getNextCheckAt().isAfter(Instant.now()))return false;
    event.setNextCheckAt(Instant.now().plusSeconds(120));return true;
 }
 @Transactional public void funding(UUID eventId,JsonNode transfer) {
    var event=events.lock(eventId).orElseThrow();
    if(!Set.of("PENDING","CONFIRMED").contains(event.getState()))return;
    boolean credited="CONFIRMED".equals(event.getState());
    if(!event.getTransferId().equals(transfer.path("id").asText()))throw new BadRequestException("Transfer identity mismatch");
    var match=wallets.findByProviderModeAndAccountNumber(event.getProviderMode(),transfer.path("account_number").asText());
    if(match.isEmpty()) {event.setState("UNMATCHED");return;}
    var wallet=wallets.lock(match.get().getId()).orElseThrow();
    String status=transfer.path("status").asText().toUpperCase(Locale.ROOT),ref=transfer.path("reference").asText();
    if(!credited && Set.of("NEW","PENDING","PROCESSING").contains(status))return;
    if(!credited && "FAILED".equals(status)) {event.setState("FAILED");return;}
    if(!"SUCCESSFUL".equals(status) || !"PSA".equals(transfer.path("debit_currency").asText())
       || !"WALLET FUNDING".equals(transfer.path("narration").asText()) || !"NGN".equals(transfer.path("currency").asText())
       || !wallet.getBankCode().equals(transfer.path("bank_code").asText()) || !ref.matches("PSA_[A-Za-z0-9_-]{1,75}")) {
        wallet.setState("REVIEW");wallet.setSyncNote("Funding verification requires support review");event.setState("REVIEW");return;
    }
    long gross=FlutterwaveGateway.kobo(transfer.path("amount")),fee=fundingFee(transfer.path("fee"));
    if(gross<=0 || fee<0 || fee>=gross)throw new BadRequestException("Invalid funding amount or fee");
    String key="psafund-"+event.getProviderMode()+"-"+event.getTransferId();
    var previous=orders.findByBusinessKey(key);
    if(previous.isPresent()) {
        var order=previous.get();
        if(!wallet.getUserId().equals(order.getUserId()) || !ref.equals(order.getReference()) || order.providerAmount()!=gross || order.getProviderFeeKobo()!=fee) {
            wallet.setState("REVIEW");wallet.setSyncNote("Previously credited funding changed; support must reconcile it");event.setState("REVIEW");return;
        }
        event.setState("CONFIRMED");event.setNextCheckAt(Instant.now().plusSeconds(3600));return;
    }
    if("LIVE".equals(wallet.getProviderMode()) && (gross>settings.getLiveMaxTransactionKobo() || gross-fee>settings.getLiveDailyLimitKobo()-orders.debits(wallet.getUserId(),Instant.now().minusSeconds(86400),List.of(MoneyOrder.Kind.DEPOSIT),List.of(MoneyOrder.Status.FAILED,MoneyOrder.Status.REVERSED)))) {
        wallet.setState("REVIEW");wallet.setSyncNote("Incoming funding exceeds pilot limits; support must reconcile received funds");event.setState("REVIEW");return;
    }
    var order=new MoneyOrder();order.setUserId(wallet.getUserId());order.setKind(MoneyOrder.Kind.DEPOSIT);order.setStatus(MoneyOrder.Status.SUCCESS);
    order.setProviderMode(event.getProviderMode());order.setDebitSubaccount(wallet.getAccountReference());order.setReference(ref);
    order.setProviderReference(event.getTransferId());order.setBusinessKey(key);order.setIdempotencyKey(key);order.setFingerprint("provider-verified-payout-funding");
    order.setAmountKobo(gross-fee);order.setProviderAmountKobo(gross);order.setProviderFeeKobo(fee);order.setNextCheckAt(Instant.now().plusSeconds(3600));
    orders.saveAndFlush(order);wallet.setBalanceKobo(Math.addExact(wallet.getBalanceKobo(),gross-fee));
    journal.save(new JournalPosting(ref+":settle",wallet.clearingAccount(),wallet.availableAccount(),gross-fee));
    event.setState("CONFIRMED");event.setNextCheckAt(Instant.now().plusSeconds(3600));wallet.setSyncRequested(true);
 }
 static long fundingFee(JsonNode fee) {
    String value=fee.asText();
    if(!value.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?"))throw new BadRequestException("Invalid funding fee");
    return new java.math.BigDecimal(value.replace(",","")).movePointRight(2).longValueExact();
 }
}
