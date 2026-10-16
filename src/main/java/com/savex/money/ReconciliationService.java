package com.savex.money;
import com.fasterxml.jackson.databind.JsonNode;
import com.savex.entity.User;
import com.savex.entity.enums.UserStatus;
import com.savex.exception.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class ReconciliationService {
    private final MoneyOrderRepository orders;
    private final JournalRepository journal;
    private final ReconciliationIssueRepository issues;
    private final SettlementMatchRepository settlements;
    private final com.savex.repository.UserRepository users;
    private final PasswordEncoder passwords;
    private final BankSettlementReceiptRepository bankReceipts;
    @PersistenceContext private EntityManager em;

    private void issue(MoneyOrder order,String reason) {
        String key=order.getReference()+":"+reason;
        if(!issues.existsByIssueKey(key)) issues.save(new ReconciliationIssue(key,order.getId(),reason));
        order.setStatus(MoneyOrder.Status.REVIEW);
        User customer=em.find(User.class,order.getUserId(),LockModeType.PESSIMISTIC_WRITE);
        if(customer!=null) customer.setStatus(UserStatus.SUSPENDED);
    }
    @Transactional
    public void dispute(String reference) {
        orders.findByReference(reference).ifPresent(found -> issue(orders.lock(found.getId()).orElseThrow(),"PROVIDER_DISPUTE_NOTIFICATION"));
    }
    @Transactional
    public void review(UUID id,String reason) { issue(orders.lock(id).orElseThrow(),reason); }
    @Transactional
    public void terminal(UUID id,ProviderResult result) {
        MoneyOrder order=orders.lock(id).orElseThrow();
        boolean credited=journal.existsByPostingKey(order.getReference()+":settle");
        boolean same=order.getReference().equals(result.reference()) && order.providerAmount()==result.amountKobo()
            && order.getCurrency().equals(result.currency())
            && (order.getProviderReference()==null || Objects.equals(order.getProviderReference(),result.providerReference()));
        if(same && credited && result.state()==ProviderResult.State.SUCCESS) return;
        if(same && credited && result.state()==ProviderResult.State.REVERSED && order.getKind()==MoneyOrder.Kind.DEPOSIT) {
            if(journal.existsByPostingKey(order.getReference()+":verified-reversal")) return;
            var wallet=em.createQuery("select w from Wallet w where w.user.id=:user",com.savex.entity.Wallet.class)
                .setParameter("user",order.getUserId()).setLockMode(LockModeType.PESSIMISTIC_WRITE).getSingleResult();
            if(wallet.getAvailableKobo()<order.getAmountKobo()) {issue(order,"REVERSAL_REQUIRES_FUNDS_RECOVERY");return;}
            wallet.setBalanceKobo(wallet.getBalanceKobo()-order.getAmountKobo());
            journal.save(new JournalPosting(order.getReference()+":verified-reversal","wallet:"+order.getUserId()+":available","provider:flutterwave:clearing",order.getAmountKobo()));
            if(order.getApplicationFeeKobo()>0) journal.save(new JournalPosting(order.getReference()+":fee-reversal","savex:fee-revenue","provider:flutterwave:clearing",order.getApplicationFeeKobo()));
            order.setStatus(MoneyOrder.Status.REVERSED);
            return;
        }
        // A disputed or changed outcome must never silently release funds or credit twice.
        issue(order,!same?"VERIFIED_DETAILS_MISMATCH":credited?"CREDITED_PAYMENT_CHANGED":"LATE_PAYMENT_OUTCOME");
    }
    @Transactional
    public int settlement(String settlementId,JsonNode data) {
        if(!settlementId.equals(data.path("id").asText()) || !"successful".equalsIgnoreCase(data.path("status").asText()))
            throw new BadRequestException("Settlement is not confirmed successful");
        JsonNode transactions=data.path("transactions");
        if(!transactions.isArray()) throw new BadRequestException("Settlement transaction breakdown is missing");
        int matched=0;
        for(JsonNode row:transactions) {
            String reference=row.path("tx_ref").asText();
            var found=orders.findByReference(reference);
            if(found.isEmpty()) continue; // This merchant account can contain unrelated business transactions.
            MoneyOrder order=orders.lock(found.get().getId()).orElseThrow();
            long gross=FlutterwaveGateway.kobo(row.path("amount"));
            long fee=FlutterwaveGateway.kobo(row.path("app_fee"));
            if(order.getKind()!=MoneyOrder.Kind.DEPOSIT || order.getStatus()!=MoneyOrder.Status.SUCCESS
                || gross!=order.providerAmount() || !"NGN".equals(row.path("currency").asText())
                || fee<0 || fee>gross || !Objects.equals(order.getProviderReference(),row.path("id").asText())) {
                issue(order,"SETTLEMENT_DETAILS_MISMATCH");continue;
            }
            var previous=settlements.findByOrderId(order.getId());
            if(previous.isPresent()) {
                SettlementMatch old=previous.get();
                if(!settlementId.equals(old.getSettlementId()) || gross!=old.getGrossKobo() || fee!=old.getProviderFeeKobo())
                    issue(order,"SETTLEMENT_CHANGED");
                continue;
            }
            settlements.save(new SettlementMatch(order.getId(),settlementId,gross,fee));
            order.setProviderFeeKobo(fee);
            if(fee>0) journal.save(new JournalPosting(order.getReference()+":provider-fee","savex:provider-fee-expense","provider:flutterwave:clearing",fee));
            // Matching a provider settlement does not assert that a bank statement was received.
            if(gross>fee) journal.save(new JournalPosting(order.getReference()+":provider-settlement",
                "provider:flutterwave:clearing","bank:settlement-awaiting-statement",gross-fee));
            matched++;
        }
        return matched;
    }
    @Transactional
    public void acknowledge(UUID reviewerId,UUID issueId,String password,String evidence) {
        User reviewer=users.findById(reviewerId).orElseThrow();
        if(!reviewer.isActive() || reviewer.getRole()!=com.savex.entity.enums.UserRole.ADMIN
            || password==null || !passwords.matches(password,reviewer.getPasswordHash())) throw new UnauthorizedException("Authorise reconciliation review");
        if(evidence==null || evidence.isBlank() || evidence.length()>200) throw new BadRequestException("Retained evidence reference required");
        ReconciliationIssue issue=em.find(ReconciliationIssue.class,issueId,LockModeType.PESSIMISTIC_WRITE);
        if(issue==null) throw new NotFoundException("Reconciliation issue not found");
        if(!"OPEN".equals(issue.getStatus())) throw new ConflictException("This issue was already acknowledged");
        issue.setStatus("ACKNOWLEDGED");issue.setReviewerId(reviewerId);issue.setEvidenceReference(evidence);issue.setResolvedAt(Instant.now());
        // Acknowledgement never repairs balances or reactivates suspended customers.
    }
    @Transactional
    public void bankReceipt(UUID reviewerId,UUID orderId,String password,long net,String evidence) {
        User reviewer=users.findById(reviewerId).orElseThrow();
        if(!reviewer.isActive() || reviewer.getRole()!=com.savex.entity.enums.UserRole.ADMIN
            || password==null || !passwords.matches(password,reviewer.getPasswordHash())) throw new UnauthorizedException("Authorise bank-statement review");
        if(evidence==null || evidence.isBlank() || evidence.length()>200) throw new BadRequestException("Retained bank statement evidence required");
        MoneyOrder order=orders.lock(orderId).orElseThrow(()->new NotFoundException("Order not found"));
        SettlementMatch match=settlements.findByOrderId(orderId).orElseThrow(()->new BadRequestException("Match provider settlement first"));
        if(order.getStatus()!=MoneyOrder.Status.SUCCESS || net<=0 || net!=match.getGrossKobo()-match.getProviderFeeKobo())
            throw new BadRequestException("Statement allocation does not match the verified net settlement");
        if(bankReceipts.existsByOrderId(orderId)) throw new ConflictException("Bank receipt already recorded");
        bankReceipts.save(new BankSettlementReceipt(orderId,reviewerId,net,evidence));
        journal.save(new JournalPosting(order.getReference()+":bank-receipt","bank:settlement-awaiting-statement","bank:confirmed-settlement",net));
    }
}
