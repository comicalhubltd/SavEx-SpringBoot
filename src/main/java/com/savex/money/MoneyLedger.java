package com.savex.money;
import com.savex.entity.*;
import com.savex.repository.*;
import com.savex.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.PageRequest;
import jakarta.persistence.*;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import static com.savex.money.MoneyOrder.*;
@Service @RequiredArgsConstructor
public class MoneyLedger {
    private final MoneySettings settings;
    private final MoneyOrderRepository orders;
    private final JournalRepository journal;
    private final WalletRepository wallets;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final CustomerReviewRepository customerReviews;
    private final com.savex.verification.EmailChallengeRepository emailChallenges;
    private final ReconciliationService reconciliation;
    private final PayoutWalletRepository payoutWallets;
    @PersistenceContext private EntityManager em;
    public static String fingerprint(MoneyDraft d) {
        try {
            String value=String.join("|",d.kind().name(),Long.toString(d.amountKobo()),d.currency(),
                Objects.toString(d.destination(),""),Objects.toString(d.serviceId(),""),Objects.toString(d.variationCode(),""),
                Objects.toString(d.groupId(),""),Objects.toString(d.cycleNumber(),""),Objects.toString(d.applicationFeeKobo(),""));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Wallet wallet(UUID user) {
        return em.createQuery("select w from Wallet w where w.user.id=:user",Wallet.class)
            .setParameter("user",user).setLockMode(LockModeType.PESSIMISTIC_WRITE).getSingleResult();
    }
    private String available(UUID user) { return "wallet:"+user+":available"; }
    private boolean hasVerifiedEmail(User user) {
        return user.isEmailVerified() && emailChallenges.findById(user.getId())
            .filter(c->c.getVerifiedAt()!=null && user.getEmail().equals(c.getEmail())).isPresent();
    }
    private String held(UUID user) { return "wallet:"+user+":held"; }
    private void post(MoneyOrder o,String event,String debit,String credit) {
        journal.save(new JournalPosting(o.getReference()+":"+event,debit,credit,o.getAmountKobo()));
    }
    @Transactional(readOnly=true)
    public Optional<MoneyView> existing(UUID userId,String key,MoneyDraft draft) {
        return orders.findByUserIdAndIdempotencyKey(userId,key).map(order->{
            if(!settings.getMode().name().equals(order.getProviderMode())) throw new ConflictException("This request belongs to another provider environment");
            if(!order.getFingerprint().equals(fingerprint(draft))) throw new ConflictException("This request key belongs to a different payment");
            return MoneyView.from(order);
        });
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MoneyView create(UUID userId,String key,MoneyDraft draft) {
        return createInternal(userId,key,draft,null,0,0);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MoneyView createPayoutWithdrawal(UUID userId,String key,MoneyDraft draft,String payoutReference,long providerAvailable,long providerFee) {
        settings.requirePayoutWallets();
        if(draft.kind()!=Kind.WITHDRAWAL)throw new BadRequestException("Funding account supports bank withdrawals only");
        return createInternal(userId,key,draft,payoutReference,providerAvailable,providerFee);
    }
    private MoneyView createInternal(UUID userId,String key,MoneyDraft draft,String payoutReference,long providerAvailable,long providerFee) {
        settings.requireEnabled();
        if(key==null || !key.matches("[A-Za-z0-9_-]{16,80}")) throw new BadRequestException("A unique payment request key is required");
        if(draft.kind()==null || !"NGN".equals(draft.currency()) || draft.amountKobo()<100 ||
            draft.amountKobo()>settings.getMaxTransactionKobo()) throw new BadRequestException("Invalid NGN payment amount");
        // Password hashing is deliberately outside the wallet critical section.
        // A second idempotency check under the lock still serialises concurrent retries.
        var prior=existing(userId,key,draft);
        if(prior.isPresent()) {
            var previous=orders.findById(prior.get().id()).orElseThrow();
            if(!Objects.equals(payoutReference,previous.getDebitSubaccount()))throw new ConflictException("This request belongs to another funding source");
            return prior.get();
        }
        boolean outgoing=draft.kind()!=Kind.DEPOSIT;
        User authoriser=users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if(outgoing && (draft.password()==null || !passwords.matches(draft.password(),authoriser.getPasswordHash())))
            throw new BadRequestException("Confirm your password to authorise this payment");
        // Acquire the same wallet lock for creation, reservation and settlement.
        Wallet w=wallet(userId);
        PayoutWallet payout=null;
        if(payoutReference!=null) {
            var found=payoutWallets.findByAccountReference(payoutReference).orElseThrow(()->new BadRequestException("Funding wallet not found"));
            payout=payoutWallets.lock(found.getId()).orElseThrow();
            if(!userId.equals(payout.getUserId()) || !settings.getMode().name().equals(payout.getProviderMode()) || !"ACTIVE".equals(payout.getState()))
                throw new BadRequestException("Funding wallet is not available for this customer");
            long reserved=payoutReserved(payoutReference);
            if(providerFee<0 || providerAvailable<Math.addExact(reserved,Math.addExact(draft.amountKobo(),providerFee)))
                throw new BadRequestException("Funding account has insufficient provider funds including transfer fees");
            long exposure=orders.payoutFeeExposure(payoutReference,List.of(Status.QUEUED,Status.SUBMITTING,Status.PENDING,Status.REVIEW));
            long fee=settings.applicationFee(draft.amountKobo());
            if(providerAvailable<Math.addExact(payout.getBalanceKobo(),Math.addExact(exposure,providerFee-fee)))
                throw new BadRequestException("This funding account needs company fee reserves before withdrawal. Customer funds cannot cover a provider-fee shortfall.");
        }
        User user=w.getUser();
        if(!user.isActive()) throw new BadRequestException("This account cannot make payments");
        long fee=settings.applicationFee(draft.amountKobo());
        long total=Math.addExact(draft.amountKobo(),fee);
        if(settings.getMode()==MoneySettings.Mode.LIVE) {
            if(orders.existsByUserIdAndProviderModeNot(userId,"LIVE")
                || (!orders.existsByUserIdAndProviderMode(userId,"LIVE") && (w.getBalanceKobo()!=0 || w.getLockedKobo()!=0)))
                throw new BadRequestException("Live customers must start with clean wallets and no test payment history");
            if((draft.kind()!=Kind.DEPOSIT && draft.kind()!=Kind.WITHDRAWAL && BillService.category(draft.kind())==null) || !settings.getLiveAllowedUsers().contains(userId)
                || !hasVerifiedEmail(user)
                || user.getKycLevel()==com.savex.entity.enums.KycStatus.UNVERIFIED
                || !customerReviews.existsByUserIdAndDecisionAndProviderMode(userId,"APPROVED","LIVE"))
                throw new BadRequestException("Live pilot requires an approved verified customer and an activated service");
            long daily=orders.debits(userId,Instant.now().minus(Duration.ofDays(1)),List.of(draft.kind()),List.of(Status.FAILED,Status.REVERSED));
            if(draft.amountKobo()>settings.getLiveMaxTransactionKobo() || total>settings.getLiveDailyLimitKobo()-daily)
                throw new BadRequestException("Live pilot payment limit exceeded");
        }
        if(settings.isFeesEnabled() && !Objects.equals(draft.applicationFeeKobo(),fee))
            throw new BadRequestException("Review and accept the current SavEx application fee before payment");
        String hash=fingerprint(draft);
        var existing=orders.findByUserIdAndIdempotencyKey(userId,key);
        if(existing.isPresent()) {
            if(!Objects.equals(payoutReference,existing.get().getDebitSubaccount()))throw new ConflictException("This request belongs to another funding source");
            if(!existing.get().getFingerprint().equals(hash)) throw new ConflictException("This request key belongs to a different payment");
            return MoneyView.from(existing.get());
        }
        if(draft.kind()==Kind.CONTRIBUTION || draft.kind()==Kind.PAYOUT)
            throw new BadRequestException("Group settlement is awaiting launch validation");
        if(draft.kind()==Kind.WITHDRAWAL) {
            if(!settings.isTransfersApproved()) throw new BadRequestException("Bank transfers are awaiting provider activation");
            if(draft.destination()==null || !draft.destination().matches("[0-9]{10}") ||
               draft.serviceId()==null || !draft.serviceId().matches("[0-9]{2,6}")) throw new BadRequestException("Invalid Nigerian bank details");
        }
        if(draft.kind()==Kind.AIRTIME || draft.kind()==Kind.DATA) {
            settings.requireBills();
            if(draft.destination()==null || !draft.destination().matches("0[789][01][0-9]{8}") ||
                draft.serviceId()==null || draft.variationCode()==null)
                throw new BadRequestException("Select a network, package, and Nigerian phone number");
        }
        if(BillService.customerCheckRequired(draft.kind())) {
            settings.requireBills();
            if(draft.destination()==null || !draft.destination().matches("[A-Za-z0-9@._+\\-]{4,64}") ||
               draft.serviceId()==null || draft.variationCode()==null)
                throw new BadRequestException("Select a bill provider, package, and customer account");
        }
        if(outgoing) {
            long used=orders.debits(userId,Instant.now().minus(Duration.ofDays(1)),
                List.of(Kind.WITHDRAWAL,Kind.AIRTIME,Kind.DATA,Kind.ELECTRICITY,Kind.CABLE,Kind.INTERNET,Kind.CONTRIBUTION),List.of(Status.FAILED,Status.REVERSED));
            if(total>settings.getDailyDebitLimitKobo()-used) throw new BadRequestException("Your daily payment limit has been reached");
            if((payout==null?w.getAvailableKobo():payout.available())<total) throw new BadRequestException("Insufficient available funds including the SavEx fee");
        }
        MoneyOrder o=new MoneyOrder();
        o.setUserId(userId); o.setIdempotencyKey(key); o.setFingerprint(hash); o.setKind(draft.kind());
        o.setAmountKobo(draft.amountKobo()); o.setReference("savex-"+UUID.randomUUID());
        o.setApplicationFeeKobo(fee);o.setProviderAmountKobo(outgoing?draft.amountKobo():total);
        o.setProviderMode(settings.getMode().name());
        o.setDebitSubaccount(payoutReference);
        if(payout!=null)o.setProviderFeeKobo(providerFee);
        o.setDestination(draft.destination()); o.setServiceId(draft.serviceId()); o.setVariationCode(draft.variationCode());
        orders.save(o);
        if(outgoing) {
            String from=payout==null?available(userId):payout.availableAccount(),to=payout==null?held(userId):payout.heldAccount();
            if(payout==null)w.setLockedKobo(Math.addExact(w.getLockedKobo(),total));else payout.setLockedKobo(Math.addExact(payout.getLockedKobo(),total));
            post(o,"reserve",from,to);
            if(fee>0) journal.save(new JournalPosting(o.getReference()+":fee-reserve",from,to,fee)); }
        return MoneyView.from(o);
    }
    public record Claim(MoneyOrder order,String email,boolean submit) {}
    @Transactional
    public Claim claim(UUID id) {
        MoneyOrder o=orders.lock(id).orElseThrow();
        // Payout funding is verified by the dedicated funding worker, never by checkout verification.
        if(o.getDebitSubaccount()!=null && o.getKind()==Kind.DEPOSIT)return null;
        if(!settings.getMode().name().equals(o.getProviderMode())) return null;
        if(!List.of(Status.QUEUED,Status.SUBMITTING,Status.PENDING,Status.SUCCESS,Status.FAILED).contains(o.getStatus()) || o.getNextCheckAt().isAfter(Instant.now())) return null;
        boolean submit=o.getStatus()==Status.QUEUED;
        // The activation flag stops new payouts, but never stops checking a transfer already sent.
        if(submit && o.getKind()==Kind.WITHDRAWAL && !settings.isTransfersApproved()) {
            o.setNextCheckAt(Instant.now().plusSeconds(60));return null;
        }
        if(submit && o.getDebitSubaccount()!=null && !settings.isPayoutWalletsEnabled()) {
            o.setNextCheckAt(Instant.now().plusSeconds(60));return null;
        }
        if(submit && o.getDebitSubaccount()!=null && payoutWallets.findByAccountReference(o.getDebitSubaccount()).filter(p->"ACTIVE".equals(p.getState()) && o.getUserId().equals(p.getUserId()) && o.getProviderMode().equals(p.getProviderMode())).isEmpty()) {
            o.setStatus(Status.REVIEW);reconciliation.review(id,"PAYOUT_WALLET_REQUIRES_REVIEW");return null;
        }
        if(submit && BillService.category(o.getKind())!=null && !settings.isBillsApproved()) {
            o.setNextCheckAt(Instant.now().plusSeconds(60));return null;
        }
        if(submit && settings.getMode()==MoneySettings.Mode.LIVE) {
            var customer=users.findById(o.getUserId()).orElseThrow();
            if(!customer.isActive() || !hasVerifiedEmail(customer) || !settings.getLiveAllowedUsers().contains(customer.getId())
                || customer.getKycLevel()==com.savex.entity.enums.KycStatus.UNVERIFIED
                || !customerReviews.existsByUserIdAndDecisionAndProviderMode(customer.getId(),"APPROVED","LIVE")) {
                o.setStatus(Status.REVIEW);return null;
            }
        }
        if(o.getStatus()!=Status.SUCCESS && o.getStatus()!=Status.FAILED) o.setStatus(submit?Status.SUBMITTING:Status.PENDING);
        o.setNextCheckAt(Instant.now().plusSeconds(120));
        return new Claim(o,users.findById(o.getUserId()).orElseThrow().getEmail(),submit);
    }
    @Transactional
    public void apply(UUID id,ProviderResult result) {
        MoneyOrder o=orders.lock(id).orElseThrow();
        if(!List.of(Status.SUBMITTING,Status.PENDING).contains(o.getStatus())) return;
        o.setNextCheckAt(Instant.now().plusSeconds(60));
        if(o.getKind()==Kind.WITHDRAWAL && o.getProviderReference()!=null && result.providerReference()!=null
            && !o.getProviderReference().equals(result.providerReference())) {
            o.setStatus(Status.REVIEW);reconciliation.review(id,"TRANSFER_ID_MISMATCH");return;
        }
        if(result.providerReference()!=null && !result.providerReference().isBlank()) o.setProviderReference(result.providerReference());
        if(result.checkoutUrl()!=null) o.setCheckoutUrl(result.checkoutUrl());
        if(o.getKind()==Kind.ELECTRICITY && o.getReference().equals(result.reference()) && result.rechargeToken()!=null && result.rechargeToken().length()<=256)
            o.setRechargeToken(result.rechargeToken());
        if(result.state()==ProviderResult.State.PENDING) { o.setStatus(Status.PENDING); return; }
        boolean same=o.getReference().equals(result.reference()) && o.providerAmount()==result.amountKobo() &&
            o.getCurrency().equals(result.currency()) &&
            (o.getKind()==Kind.DEPOSIT || Objects.equals(o.getDestination(),result.destination()));
        if(!same || result.state()==ProviderResult.State.REVIEW || result.state()==ProviderResult.State.REVERSED) {
            o.setStatus(Status.REVIEW);reconciliation.review(id,"PAYMENT_REQUIRES_REVIEW"); return;
        }
        Wallet w=wallet(o.getUserId());
        if(o.getDebitSubaccount()!=null) { applyPayout(o,result);return; }
        if(result.state()==ProviderResult.State.SUCCESS) {
            if(o.getKind()==Kind.DEPOSIT) {
                w.setBalanceKobo(Math.addExact(w.getBalanceKobo(),o.getAmountKobo()));
                post(o,"settle","provider:flutterwave:clearing",available(o.getUserId()));
            } else {
                if(w.getLockedKobo()<o.totalDebit() || w.getBalanceKobo()<o.totalDebit()) throw new IllegalStateException("Wallet reserve invariant failed");
                w.setLockedKobo(w.getLockedKobo()-o.totalDebit()); w.setBalanceKobo(w.getBalanceKobo()-o.totalDebit());
                post(o,"settle",held(o.getUserId()),"provider:flutterwave:clearing");
            }
            if(o.getApplicationFeeKobo()>0) journal.save(new JournalPosting(o.getReference()+":application-fee",
                o.getKind()==Kind.DEPOSIT?"provider:flutterwave:clearing":held(o.getUserId()),"savex:fee-revenue",o.getApplicationFeeKobo()));
            o.setProviderFeeKobo(result.feeKobo());
            if(o.getKind()!=Kind.DEPOSIT && result.feeKobo()>0)
                journal.save(new JournalPosting(o.getReference()+(o.getKind()==Kind.WITHDRAWAL?":provider-transfer-fee":":provider-bill-fee"),"savex:provider-fee-expense","provider:flutterwave:clearing",result.feeKobo()));
            o.setStatus(Status.SUCCESS);
            o.setNextCheckAt(Instant.now().plusSeconds(3600));
        } else if(result.state()==ProviderResult.State.FAILED) {
            if(o.getKind()!=Kind.DEPOSIT) {
                if(w.getLockedKobo()<o.totalDebit()) throw new IllegalStateException("Wallet reserve invariant failed");
                w.setLockedKobo(w.getLockedKobo()-o.totalDebit()); post(o,"release",held(o.getUserId()),available(o.getUserId()));
                if(o.getApplicationFeeKobo()>0) journal.save(new JournalPosting(o.getReference()+":fee-release",held(o.getUserId()),available(o.getUserId()),o.getApplicationFeeKobo()));
            }
            o.setStatus(Status.FAILED);
            o.setNextCheckAt(Instant.now().plusSeconds(3600));
        }
    }
    private long payoutReserved(String ref) {
        return orders.payoutReserved(ref,List.of(Status.QUEUED,Status.SUBMITTING,Status.PENDING,Status.REVIEW));
    }
    private void applyPayout(MoneyOrder order,ProviderResult result) {
        if(order.getKind()!=Kind.WITHDRAWAL)throw new IllegalStateException("Unsupported payout settlement");
        var found=payoutWallets.findByAccountReference(order.getDebitSubaccount()).orElseThrow();
        var wallet=payoutWallets.lock(found.getId()).orElseThrow();
        if(!order.getUserId().equals(wallet.getUserId()) || !order.getProviderMode().equals(wallet.getProviderMode())
           || wallet.getLockedKobo()<order.totalDebit() || wallet.getBalanceKobo()<order.totalDebit())throw new IllegalStateException("Payout reserve invariant failed");
        if(result.state()==ProviderResult.State.SUCCESS) {
            wallet.setLockedKobo(wallet.getLockedKobo()-order.totalDebit());wallet.setBalanceKobo(wallet.getBalanceKobo()-order.totalDebit());
            post(order,"settle",wallet.heldAccount(),wallet.clearingAccount());
            if(order.getApplicationFeeKobo()>0)journal.save(new JournalPosting(order.getReference()+":application-fee",wallet.heldAccount(),"savex:fee-revenue",order.getApplicationFeeKobo()));
            if(result.feeKobo()>0)journal.save(new JournalPosting(order.getReference()+":provider-transfer-fee","savex:provider-fee-expense",wallet.clearingAccount(),result.feeKobo()));
            if(result.feeKobo()>order.getProviderFeeKobo()) {wallet.setState("REVIEW");wallet.setSyncNote("Provider charged more than the reserved fee. Company funding and reconciliation are required.");}
            order.setProviderFeeKobo(result.feeKobo());order.setStatus(Status.SUCCESS);
        }else if(result.state()==ProviderResult.State.FAILED) {
            wallet.setLockedKobo(wallet.getLockedKobo()-order.totalDebit());post(order,"release",wallet.heldAccount(),wallet.availableAccount());
            if(order.getApplicationFeeKobo()>0)journal.save(new JournalPosting(order.getReference()+":fee-release",wallet.heldAccount(),wallet.availableAccount(),order.getApplicationFeeKobo()));
            order.setStatus(Status.FAILED);
        }
        wallet.setSyncRequested(true);order.setNextCheckAt(Instant.now().plusSeconds(3600));
    }
    @Transactional
    public void uncertain(UUID id) {
        MoneyOrder o=orders.lock(id).orElseThrow();
        if(o.getStatus()==Status.SUBMITTING || o.getStatus()==Status.PENDING) {
            o.setStatus(Status.PENDING); o.setNextCheckAt(Instant.now().plusSeconds(60));
        }
    }
    @Transactional
    public void notifyReference(String reference) {
        orders.findByReference(reference).ifPresent(found -> {
            MoneyOrder o=orders.lock(found.getId()).orElseThrow();
            if(o.getStatus()!=Status.REVIEW && o.getStatus()!=Status.REVERSED) o.setNextCheckAt(Instant.now());
        });
    }
    @Transactional(readOnly=true)
    public List<MoneyView> history(UUID user) {
        return orders.findByUserIdOrderByCreatedAtDesc(user,PageRequest.of(0,50)).map(MoneyView::from).getContent();
    }
}
