package com.savex.money;
import com.savex.entity.*;
import com.savex.entity.enums.*;
import com.savex.repository.*;
import com.savex.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:moneytests;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=GROUPS;LOCK_TIMEOUT=30000","savex.money.mode=TEST","savex.money.flutterwave-secret=FLWSECK_TEST-fixture-X",
    "savex.money.webhook-secret=fixture-secret-never-use-outside-tests-123456",
    "savex.money.worker-enabled=false","savex.money.fees-enabled=false","savex.money.transfers-approved=true","savex.money.bills-approved=true"})
@ActiveProfiles("dev")
class MoneyLedgerTest {
    @Autowired PayoutWalletStore payoutStore;
    @Autowired PayoutWalletRepository payoutWallets;
    @Autowired PayoutFundingEventRepository payoutEvents;
    @Autowired PayoutWalletReport payoutReports;
    com.fasterxml.jackson.databind.JsonNode funding(PayoutWallet wallet,String transfer,String ref,String status) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"id\":\""+transfer+"\",\"reference\":\""+ref+"\",\"account_number\":\""+wallet.getAccountNumber()+"\",\"bank_code\":\"044\",\"currency\":\"NGN\",\"debit_currency\":\"PSA\",\"narration\":\"WALLET FUNDING\",\"status\":\""+status+"\",\"amount\":1000,\"fee\":10}");
    }
    PayoutWallet payout(UUID user,long balance) {
        var wallet=new PayoutWallet();wallet.setUserId(user);wallet.setProviderMode("TEST");wallet.setState("ACTIVE");
        wallet.setAccountReference("PSA"+UUID.randomUUID().toString().replace("-",""));wallet.setAccountNumber(String.format("%010d",Math.abs(UUID.randomUUID().getLeastSignificantBits()%10000000000L)));
        wallet.setBankCode("044");wallet.setBalanceKobo(balance);return payoutWallets.saveAndFlush(wallet);
    }
    UUID fundingEvent(String id) {payoutStore.enqueue("TEST",id);return payoutEvents.findAll().stream().filter(e->id.equals(e.getTransferId())).findFirst().orElseThrow().getId();}
    @Test void payoutFundingIsVerifiedOnceSeparateFromCheckoutAndChangesRequireReview() throws Exception {
        UUID user=saver(20000);var wallet=payout(user,0);String transfer=""+Math.abs(UUID.randomUUID().getLeastSignificantBits());String ref="PSA_"+transfer;
        UUID event=fundingEvent(transfer);var data=funding(wallet,transfer,ref,"SUCCESSFUL");
        payoutStore.funding(event,data);payoutStore.funding(event,data);
        assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getBalanceKobo()).isEqualTo(99000);
        assertThat(wallets.findByUserId(user).orElseThrow().getBalanceKobo()).isEqualTo(20000);
        var order=orders.findByReference(ref).orElseThrow();assertThat(order.getDebitSubaccount()).isEqualTo(wallet.getAccountReference());
        assertThat(ledger.claim(order.getId())).isNull();
        assertThatThrownBy(()->inline.prepare(user,order.getId())).isInstanceOf(BadRequestException.class);
        var row=payoutReports.report(0).content().stream().filter(r->user.equals(r.customerId())).findFirst().orElseThrow();assertThat(row.matched()).isTrue();
        payoutStore.funding(event,funding(wallet,transfer,ref,"REVERSED"));
        assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getState()).isEqualTo("REVIEW");
        assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getBalanceKobo()).isEqualTo(99000);
    }
    @Test void payoutWithdrawalReservesItsOwnSourceAndSettlesOrReleasesOnlyOnce() {
        boolean prior=settings.isPayoutWalletsEnabled(),fees=settings.isFeesEnabled();settings.setPayoutWalletsEnabled(true);settings.setFeesEnabled(true);
        try {
            UUID user=saver(20000);var wallet=payout(user,100000);var draft=feeDraft(MoneyOrder.Kind.WITHDRAWAL,50000,250);String key=UUID.randomUUID().toString();
            var view=ledger.createPayoutWithdrawal(user,key,draft,wallet.getAccountReference(),101000,1000);
            assertThat(ledger.createPayoutWithdrawal(user,key,draft,wallet.getAccountReference(),0,0).id()).isEqualTo(view.id());
            assertThatThrownBy(()->ledger.create(user,key,draft)).hasMessageContaining("another funding source");
            assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getLockedKobo()).isEqualTo(50250);
            assertThat(wallets.findByUserId(user).orElseThrow().getLockedKobo()).isZero();
            due(view.id());ledger.claim(view.id());ledger.apply(view.id(),result(view,ProviderResult.State.SUCCESS,50000));ledger.apply(view.id(),result(view,ProviderResult.State.SUCCESS,50000));
            assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getBalanceKobo()).isEqualTo(49750);
            assertThat(wallets.findByUserId(user).orElseThrow().getBalanceKobo()).isEqualTo(20000);
            var failure=ledger.createPayoutWithdrawal(user,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.WITHDRAWAL,10000,50),wallet.getAccountReference(),51000,1000);
            due(failure.id());ledger.claim(failure.id());ledger.apply(failure.id(),result(failure,ProviderResult.State.FAILED,10000));ledger.apply(failure.id(),result(failure,ProviderResult.State.FAILED,10000));
            assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getLockedKobo()).isZero();
            assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getBalanceKobo()).isEqualTo(49750);
            assertThatThrownBy(()->ledger.createPayoutWithdrawal(saver(100000),UUID.randomUUID().toString(),draft,wallet.getAccountReference(),100000,1000)).hasMessageContaining("not available for this customer");
        }finally{settings.setPayoutWalletsEnabled(prior);settings.setFeesEnabled(fees);}
    }
    @Test void payoutCreationIsDurableAndNeverRetriedBlindly() {
        UUID user=saver(0);var first=payoutStore.begin(user,"TEST");assertThat(first.create()).isTrue();
        payoutStore.uncertain(first.wallet().getId());var second=payoutStore.begin(user,"TEST");assertThat(second.create()).isFalse();
        assertThat(second.wallet().getId()).isEqualTo(first.wallet().getId());assertThat(second.wallet().getState()).isEqualTo("CREATION_UNCERTAIN");
    }
    @Test void payoutBindingRequiresProviderOwnershipAndValidNgnAccount() throws Exception {
        UUID user=saver(0);var wallet=payoutStore.begin(user,"TEST").wallet();String email=users.findById(user).orElseThrow().getEmail();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var remote=mapper.createObjectNode().put("account_reference","PSA"+UUID.randomUUID().toString().replace("-","")).put("barter_id","234000000123456").put("account_name","Test Saver").put("email","other@example.com").put("country","NG").put("status","ACTIVE");
        assertThatThrownBy(()->payoutStore.bind(wallet.getId(),remote,email)).hasMessageContaining("ownership");
        assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getAccountReference()).isNull();
        remote.put("email",email);payoutStore.bind(wallet.getId(),remote,email);
        var account=mapper.createObjectNode().put("static_account",String.format("%010d",Math.abs(UUID.randomUUID().getLeastSignificantBits()%10000000000L))).put("bank_code","044").put("bank_name","Fixture Bank").put("currency","USD");
        assertThatThrownBy(()->payoutStore.account(wallet.getId(),account)).hasMessageContaining("details");
        account.put("currency","NGN");payoutStore.account(wallet.getId(),account);
        assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getState()).isEqualTo("ACTIVE");
    }
    @Test void concurrentPayoutWithdrawalsCannotOverReserveProviderOrCustomerFunds() throws Exception {
        boolean prior=settings.isPayoutWalletsEnabled();settings.setPayoutWalletsEnabled(true);
        try {
            var wallet=payout(saver(0),100000);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
            try {
                java.util.concurrent.Callable<Boolean> attempt=()->{start.await();try {ledger.createPayoutWithdrawal(wallet.getUserId(),UUID.randomUUID().toString(),draft(MoneyOrder.Kind.WITHDRAWAL,60000),wallet.getAccountReference(),102000,1000);return true;}catch(BadRequestException rejected){return false;}};
                var first=pool.submit(attempt);var second=pool.submit(attempt);start.countDown();
                assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
                assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getLockedKobo()).isEqualTo(60000);
            }finally{pool.shutdownNow();}
        }finally{settings.setPayoutWalletsEnabled(prior);}
    }
    @Test void pendingOrWrongCurrencyPayoutFundingNeverCredits() throws Exception {
        var wallet=payout(saver(0),0);String transfer=""+Math.abs(UUID.randomUUID().getLeastSignificantBits());UUID event=fundingEvent(transfer);
        payoutStore.funding(event,funding(wallet,transfer,"PSA_"+transfer,"PENDING"));assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getBalanceKobo()).isZero();
        var changed=(com.fasterxml.jackson.databind.node.ObjectNode)funding(wallet,transfer,"PSA_"+transfer,"SUCCESSFUL");changed.put("currency","USD");payoutStore.funding(event,changed);
        assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getBalanceKobo()).isZero();assertThat(payoutWallets.findById(wallet.getId()).orElseThrow().getState()).isEqualTo("REVIEW");
    }
    @Autowired AiRecordRepository aiRecords;
    @Autowired AiInvestigationService aiInvestigations;
    @Autowired AiBudget aiBudget;
    @Autowired AiSettings aiSettings;
    @Test void aiFeedbackLearningAndBudgetPersistWithoutFinancialChanges() {
        long balances=wallets.findAll().stream().mapToLong(Wallet::getBalanceKobo).sum(),postings=journal.count();
        UUID actor=UUID.randomUUID();
        var run=aiRecords.saveAndFlush(new AiRecord("RUN","UNVERIFIED",null,actor,null,"{}"));
        aiInvestigations.feedback(actor,run.getId(),AiInvestigationService.Feedback.UNCLEAR_NEXT_STEP);
        assertThatThrownBy(()->aiInvestigations.feedback(actor,run.getId(),AiInvestigationService.Feedback.HELPFUL)).hasMessageContaining("already recorded");
        var day=java.time.LocalDate.now(java.time.ZoneId.of("Africa/Lagos"));
        var report=aiInvestigations.summarize(day);
        assertThat(report.content().path("feedbackCounts").path("UNCLEAR_NEXT_STEP").asInt()).isEqualTo(1);
        var lessons=aiRecords.findTop10ByKindAndStateOrderByCreatedAtDesc("LESSON","PENDING");assertThat(lessons).isNotEmpty();
        var lesson=lessons.getFirst();aiInvestigations.review(actor,lesson.getId(),true);
        assertThat(aiRecords.findById(lesson.getId()).orElseThrow().getState()).isEqualTo("APPROVED");
        aiInvestigations.review(actor,lesson.getId(),false);
        assertThat(aiRecords.findById(lesson.getId()).orElseThrow().getState()).isEqualTo("REJECTED");
        assertThat(aiRecords.findTop100ByKindOrderByCreatedAtDesc("REVIEW")).hasSize(2);
        int prior=aiSettings.getDailyLimit();aiSettings.setDailyLimit(1);
        try{aiBudget.reserve();assertThatThrownBy(aiBudget::reserve).hasMessageContaining("Daily AI request limit");}
        finally{aiSettings.setDailyLimit(prior);}
        assertThat(wallets.findAll().stream().mapToLong(Wallet::getBalanceKobo).sum()).isEqualTo(balances);assertThat(journal.count()).isEqualTo(postings);
    }
    @Autowired MoneyLedger ledger;
    @Autowired MoneyOrderRepository orders;
    @Autowired JournalRepository journal;
    @Autowired UserRepository users;
    @Autowired WalletRepository wallets;
    @Autowired PasswordEncoder passwords;
    @Autowired MoneySettings settings;
    @Autowired ReconciliationService reconciliation;
    @Autowired ReconciliationIssueRepository issues;
    @Autowired CustomerReviewService customerReviews;
    @Autowired IdentityRepository identities;
    @Autowired SettlementMatchRepository settlementMatches;
    @Autowired com.savex.verification.EmailChallengeRepository emailChallenges;
    @Autowired com.savex.verification.AccountAssessmentService assessments;
    @Autowired InlineCheckoutService inline;
    @Autowired WalletAuditService walletAudit;
    @Autowired PaymentReceiptService receipts;
    WalletAuditService.Row auditRow(UUID user) {
        for(int page=0;page<100;page++) {
            var report=walletAudit.report(page);
            var found=report.content().stream().filter(row->user.equals(row.customerId())).findFirst();
            if(found.isPresent())return found.get();
            if(report.last())break;
        }
        throw new AssertionError("Wallet missing from report");
    }
    @Test void auditMatchesVerifiedDepositAndDetectsUnjournalledChangeWithoutRepairingIt() {
        UUID user=saver(0);
        var order=ledger.create(user,UUID.randomUUID().toString(),draft(MoneyOrder.Kind.DEPOSIT,10000));
        due(order.id());ledger.claim(order.id());
        ledger.apply(order.id(),new ProviderResult(ProviderResult.State.SUCCESS,order.reference(),10000,"NGN",null,"123",null,0));
        assertThat(auditRow(user).matched()).isTrue();
        assertThat(auditRow(user).journalAvailableKobo()).isEqualTo(10000);
        var receipt=receipts.receipt(user,order.id());assertThat(receipt.authorizedTotalKobo()).isEqualTo(10000);
        assertThatThrownBy(()->receipts.receipt(UUID.randomUUID(),order.id())).isInstanceOf(NotFoundException.class);
        var wallet=wallets.findByUserId(user).orElseThrow();wallet.setBalanceKobo(11000);wallets.saveAndFlush(wallet);
        assertThat(auditRow(user).matched()).isFalse();assertThat(auditRow(user).differenceKobo()).isEqualTo(1000);
        assertThat(wallets.findByUserId(user).orElseThrow().getBalanceKobo()).isEqualTo(11000);
    }
    @Test void changedFeePolicyRejectsStaleCustomerQuoteButPreservesSavedOrderFee() {
        settings.setFeesEnabled(true);
        try {
            UUID user=saver(0);
            var order=ledger.create(user,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.DEPOSIT,100000,500));
            settings.setFeeBasisPoints(100);
            assertThatThrownBy(()->ledger.create(user,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.DEPOSIT,100000,500)))
                .isInstanceOf(BadRequestException.class);
            assertThat(orders.findById(order.id()).orElseThrow().getApplicationFeeKobo()).isEqualTo(500);
        } finally {settings.setFeeBasisPoints(50);settings.setFeesEnabled(false);}
    }
    @Test void inlineCheckoutUsesSavedAmountsAndCustomerAndCallbackNeverCreditsWallet() {
        String priorKey=settings.getFlutterwavePublicKey();boolean priorFees=settings.isFeesEnabled();
        settings.setFlutterwavePublicKey("FLWPUBK_TEST-fixture-X");settings.setFeesEnabled(true);
        try {
            UUID customer=saver(0),other=saver(0);
            var draft=feeDraft(MoneyOrder.Kind.DEPOSIT,50000,250);
            var order=ledger.create(customer,UUID.randomUUID().toString(),draft);
            assertThatThrownBy(()->inline.prepare(other,order.id())).isInstanceOf(NotFoundException.class);
            var config=inline.prepare(customer,order.id());
            assertThat(config.tx_ref()).isEqualTo(order.reference());assertThat(config.amount()).isEqualByComparingTo("502.50");
            assertThat(config.customer().email()).isEqualTo(users.findById(customer).orElseThrow().getEmail());
            assertThat(orders.findById(order.id()).orElseThrow().getStatus()).isEqualTo(MoneyOrder.Status.PENDING);
            assertThat(inline.prepare(customer,order.id()).tx_ref()).isEqualTo(order.reference());
            inline.check(customer,order.id());
            assertThat(wallets.findByUserId(customer).orElseThrow().getBalanceKobo()).isZero();
            assertThat(journal.existsByPostingKey(order.reference()+":settle")).isFalse();
            var gateway=new FlutterwaveGateway(settings,org.springframework.web.reactive.function.client.WebClient.builder());
            assertThat(gateway.submit(orders.findById(order.id()).orElseThrow(),config.customer().email()).checkoutUrl()).isNull();
        } finally {settings.setFlutterwavePublicKey(priorKey);settings.setFeesEnabled(priorFees);}
    }
    @Test void inlineCheckoutRejectsMissingOrWrongEnvironmentKey() {
        String priorKey=settings.getFlutterwavePublicKey();
        try {
            settings.setFlutterwavePublicKey("");assertThatThrownBy(settings::requireInline).isInstanceOf(BadRequestException.class);
            settings.setFlutterwavePublicKey("FLWPUBK-live-fixture-X");assertThatThrownBy(settings::requireInline).isInstanceOf(BadRequestException.class);
        } finally {settings.setFlutterwavePublicKey(priorKey);}
    }
    @Test void automaticChecksDoNotApproveIdentityAndExplainFundingBlockers() {
        UUID customer=saver(0);var user=users.findById(customer).orElseThrow();user.setStatus(UserStatus.PENDING_VERIFICATION);users.saveAndFlush(user);verifiedEmail(customer);
        var priorMode=settings.getMode();settings.setMode(MoneySettings.Mode.LIVE);
        try {
            var report=assessments.assess(customer);
            assertThat(report.emailVerified()).isTrue();assertThat(report.fundingAvailable()).isFalse();
            assertThat(report.reviewState()).isIn("READY_FOR_EVIDENCE_REVIEW","DATA_NEEDS_ATTENTION");
            assertThat(report.fundingBlockers()).anyMatch(s->s.contains("identity evidence")).anyMatch(s->s.contains("allowlist"));
            assertThat(assessments.assess(customer).reportId()).isEqualTo(report.reportId());
            assertThat(users.findById(customer).orElseThrow().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(customerReviewsCount(customer)).isZero();
        } finally {settings.setMode(priorMode);}
    }
    long customerReviewsCount(UUID id) {return assessments.assess(id).checks().stream().filter(c->c.name().equals("LIVE review") && c.passed()).count();}
    @Test void concurrentAccountReportsKeepCustomersIsolated() throws Exception {
        var ids=new ArrayList<UUID>();for(int i=0;i<8;i++)ids.add(saver(0));verifiedEmail(ids.getFirst());
        var pool=Executors.newFixedThreadPool(4);
        try {
            var tasks=ids.stream().<Callable<com.savex.verification.AccountAssessmentService.Assessment>>map(id->()->assessments.assess(id)).toList();
            var results=pool.invokeAll(tasks);
            for(int i=0;i<ids.size();i++) {var report=results.get(i).get();assertThat(report.customerId()).isEqualTo(ids.get(i));assertThat(report.emailVerified()).isEqualTo(i==0);}
            var batch=assessments.assessPage(new org.springframework.data.domain.PageImpl<>(users.findAllById(ids)));
            assertThat(batch.getContent()).extracting(com.savex.verification.AccountAssessmentService.Assessment::customerId).containsExactlyInAnyOrderElementsOf(ids);
        } finally {pool.shutdownNow();}
    }
    void verifiedEmail(UUID id) {
        var user=users.findById(id).orElseThrow();user.setEmailVerified(true);users.saveAndFlush(user);
        var c=new com.savex.verification.EmailChallenge();c.setUserId(id);c.setEmail(user.getEmail());c.setVerifiedAt(Instant.now());emailChallenges.saveAndFlush(c);
    }
    @Test void liveManualEvidenceApprovalDoesNotRequireBvnOrEnabledPayments() {
        UUID customer=saver(0),reviewer=saver(0);verifiedEmail(customer);
        var admin=users.findById(reviewer).orElseThrow();admin.setRole(UserRole.ADMIN);users.saveAndFlush(admin);
        settings.setMode(MoneySettings.Mode.OFF);
        try {
            assertThatThrownBy(()->customerReviews.review(reviewer,customer,"TestPassword1!","case-1",true,true,true,
                "DOCUMENT_REVIEW","too short","LIVE")).isInstanceOf(BadRequestException.class);
            customerReviews.review(reviewer,customer,"TestPassword1!","retained-document-case",true,true,true,
                "DOCUMENT_REVIEW","Fixture reviewer checked original evidence and matched the customer's identity.","LIVE");
            assertThat(assessments.assess(customer).accountStatus()).isEqualTo("ACTIVE");
            assertThat(identities.findByUserId(customer)).isEmpty();
            assertThat(users.findById(customer).orElseThrow().getKycLevel()).isEqualTo(KycStatus.REVIEWED);
            settings.setMode(MoneySettings.Mode.LIVE);settings.setFeesEnabled(true);settings.setLiveAllowedUsers(Set.of(customer));
            assertThat(ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.DEPOSIT,100000,500)).providerAmountKobo()).isEqualTo(100500);
            assertThatThrownBy(()->customerReviews.review(customer,customer,"TestPassword1!","self-case",true,true,true,
                "DOCUMENT_REVIEW","Identity review cannot be performed by the customer.","LIVE")).isInstanceOf(UnauthorizedException.class);
        } finally {settings.setMode(MoneySettings.Mode.TEST);settings.setFeesEnabled(false);settings.setLiveAllowedUsers(Set.of());}
    }
    @Test void settlementMatchingPostsProviderFeesOnceAndFlagsChangedRecords() throws Exception {
        UUID user=saver(0);var order=ledger.create(user,UUID.randomUUID().toString(),draft(MoneyOrder.Kind.DEPOSIT,100000));
        due(order.id());ledger.claim(order.id());ledger.apply(order.id(),result(order,ProviderResult.State.SUCCESS,100000));
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var data=json.readTree("{\"id\":42,\"status\":\"successful\",\"transactions\":[{\"tx_ref\":\""+order.reference()+"\",\"id\":123,\"amount\":1000,\"app_fee\":10,\"currency\":\"NGN\"}]}");
        assertThat(reconciliation.settlement("42",data)).isEqualTo(1);
        assertThat(reconciliation.settlement("42",data)).isZero();
        assertThat(settlementMatches.findByOrderId(order.id()).orElseThrow().getProviderFeeKobo()).isEqualTo(1000);
        assertThat(journal.existsByPostingKey(order.reference()+":provider-settlement")).isTrue();
        UUID reviewer=saver(0);var admin=users.findById(reviewer).orElseThrow();admin.setRole(UserRole.ADMIN);users.saveAndFlush(admin);
        assertThatThrownBy(()->reconciliation.bankReceipt(reviewer,order.id(),"TestPassword1!",98000,"bank-case"))
            .isInstanceOf(BadRequestException.class);
        reconciliation.bankReceipt(reviewer,order.id(),"TestPassword1!",99000,"retained-bank-statement");
        assertThat(journal.existsByPostingKey(order.reference()+":bank-receipt")).isTrue();
        assertThatThrownBy(()->reconciliation.bankReceipt(reviewer,order.id(),"TestPassword1!",99000,"duplicate-statement"))
            .isInstanceOf(ConflictException.class);
        ((com.fasterxml.jackson.databind.node.ObjectNode)data.path("transactions").get(0)).put("amount",999);
        reconciliation.settlement("42",data);
        assertThat(users.findById(user).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(wallets.findByUserId(user).orElseThrow().getBalanceKobo()).isEqualTo(100000);
    }
    @Test void workerNeverClaimsAnOrderFromAnotherProviderEnvironment() {
        UUID user=saver(0);var order=ledger.create(user,UUID.randomUUID().toString(),draft(MoneyOrder.Kind.DEPOSIT,10000));
        var saved=orders.findById(order.id()).orElseThrow();saved.setProviderMode("LIVE");orders.saveAndFlush(saved);
        due(order.id());assertThat(ledger.claim(order.id())).isNull();
        assertThat(orders.findById(order.id()).orElseThrow().getStatus()).isEqualTo(MoneyOrder.Status.QUEUED);
    }
    @Test void livePilotRequiresReviewCleanWalletAndEnforcesFeeInclusiveDailyLimit() {
        UUID customer=saver(0),reviewer=saver(0),testCustomer=saver(0);
        ledger.create(testCustomer,UUID.randomUUID().toString(),draft(MoneyOrder.Kind.DEPOSIT,10000));
        var admin=users.findById(reviewer).orElseThrow();admin.setRole(UserRole.ADMIN);users.saveAndFlush(admin);
        settings.setMode(MoneySettings.Mode.LIVE);settings.setFeesEnabled(true);settings.setLiveAllowedUsers(Set.of(customer,testCustomer));
        try {
            assertThatThrownBy(()->ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.DEPOSIT,1000000,5000)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("approved verified");
            assertThatThrownBy(()->ledger.create(testCustomer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.DEPOSIT,1000000,5000)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("no test payment history");
            IdentityCheck evidence=new IdentityCheck();evidence.setUserId(customer);evidence.setStatus("EVIDENCE_RECEIVED");evidence.setProviderMode("LIVE");identities.saveAndFlush(evidence);
            verifiedEmail(customer);
            customerReviews.review(reviewer,customer,"TestPassword1!","retained-live-fixture-evidence",true,false,true,true);
            var funding=ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.DEPOSIT,1000000,5000));
            assertThat(funding.providerAmountKobo()).isEqualTo(1005000);
            assertThatThrownBy(()->ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.DEPOSIT,1000000,5000)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("limit exceeded");
            assertThatThrownBy(()->ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.WITHDRAWAL,1000000,5000)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Insufficient available funds");
        } finally {settings.setMode(MoneySettings.Mode.TEST);settings.setFeesEnabled(false);settings.setLiveAllowedUsers(Set.of());}
    }
    @Test void liveWithdrawalUsesFundedWalletKeepsReservationsOnTimeoutAndSettlesOnceWithFees() {
        UUID customer=saver(0),reviewer=saver(0);verifiedEmail(customer);
        var admin=users.findById(reviewer).orElseThrow();admin.setRole(UserRole.ADMIN);users.saveAndFlush(admin);
        customerReviews.review(reviewer,customer,"TestPassword1!","live-withdrawal-fixture",true,true,true,
            "DOCUMENT_REVIEW","Reviewer checked genuine identity evidence for this withdrawal fixture.","LIVE");
        settings.setMode(MoneySettings.Mode.LIVE);settings.setFeesEnabled(true);settings.setLiveAllowedUsers(Set.of(customer));
        try {
            var funding=ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.DEPOSIT,100000,500));
            due(funding.id());ledger.claim(funding.id());
            ledger.apply(funding.id(),result(funding,ProviderResult.State.SUCCESS,100500));
            String key=UUID.randomUUID().toString();
            var withdrawal=ledger.create(customer,key,feeDraft(MoneyOrder.Kind.WITHDRAWAL,50000,250));
            assertThat(ledger.create(customer,key,feeDraft(MoneyOrder.Kind.WITHDRAWAL,50000,250)).id()).isEqualTo(withdrawal.id());
            assertThat(wallets.findByUserId(customer).orElseThrow().getLockedKobo()).isEqualTo(50250);
            settings.setTransfersApproved(false);due(withdrawal.id());assertThat(ledger.claim(withdrawal.id())).isNull();
            settings.setTransfersApproved(true);due(withdrawal.id());assertThat(ledger.claim(withdrawal.id()).submit()).isTrue();
            ledger.uncertain(withdrawal.id());settings.setTransfersApproved(false);due(withdrawal.id());
            assertThat(ledger.claim(withdrawal.id()).submit()).isFalse();
            var success=new ProviderResult(ProviderResult.State.SUCCESS,withdrawal.reference(),50000,"NGN",withdrawal.destination(),"456",null,1000);
            ledger.apply(withdrawal.id(),success);ledger.apply(withdrawal.id(),success);
            var wallet=wallets.findByUserId(customer).orElseThrow();
            assertThat(wallet.getBalanceKobo()).isEqualTo(49750);assertThat(wallet.getLockedKobo()).isZero();
            assertThat(orders.findById(withdrawal.id()).orElseThrow().getProviderFeeKobo()).isEqualTo(1000);
            assertThat(journal.findAll().stream().filter(p->p.getPostingKey().equals(withdrawal.reference()+":provider-transfer-fee")).count()).isEqualTo(1);
            assertThatThrownBy(()->ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.WITHDRAWAL,10000,50)))
                .hasMessageContaining("awaiting provider activation");
            settings.setTransfersApproved(true);settings.setLiveAllowedUsers(Set.of());
            assertThatThrownBy(()->ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.WITHDRAWAL,10000,50)))
                .hasMessageContaining("approved verified");
        } finally {settings.setMode(MoneySettings.Mode.TEST);settings.setFeesEnabled(false);settings.setTransfersApproved(true);settings.setLiveAllowedUsers(Set.of());}
    }
    @Test void liveBillsReserveAndSettleOnceReleaseFailuresAndRespectActivation() {
        UUID customer=saver(0),reviewer=saver(0);verifiedEmail(customer);
        var admin=users.findById(reviewer).orElseThrow();admin.setRole(UserRole.ADMIN);users.saveAndFlush(admin);
        customerReviews.review(reviewer,customer,"TestPassword1!","live-bill-fixture",true,true,true,
            "DOCUMENT_REVIEW","Reviewer matched genuine identity evidence for bill-payment testing.","LIVE");
        settings.setMode(MoneySettings.Mode.LIVE);settings.setFeesEnabled(true);settings.setLiveAllowedUsers(Set.of(customer));
        try {
            var funding=ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.DEPOSIT,50000,250));due(funding.id());ledger.claim(funding.id());ledger.apply(funding.id(),result(funding,ProviderResult.State.SUCCESS,50250));
            var draft=new MoneyDraft(MoneyOrder.Kind.AIRTIME,20000,"NGN","08012345678","BIL001","AT001",null,null,"TestPassword1!",null,100L);
            String key=UUID.randomUUID().toString();var airtime=ledger.create(customer,key,draft);
            assertThat(ledger.create(customer,key,draft).id()).isEqualTo(airtime.id());
            assertThat(wallets.findByUserId(customer).orElseThrow().getLockedKobo()).isEqualTo(20100);
            settings.setBillsApproved(false);due(airtime.id());assertThat(ledger.claim(airtime.id())).isNull();
            settings.setBillsApproved(true);due(airtime.id());ledger.claim(airtime.id());
            settings.setBillsApproved(false);ledger.uncertain(airtime.id());due(airtime.id());assertThat(ledger.claim(airtime.id()).submit()).isFalse();
            var success=new ProviderResult(ProviderResult.State.SUCCESS,airtime.reference(),20000,"NGN","08012345678","CF-airtime",null,200);
            ledger.apply(airtime.id(),success);ledger.apply(airtime.id(),success);
            assertThat(wallets.findByUserId(customer).orElseThrow().getBalanceKobo()).isEqualTo(29900);
            assertThat(journal.existsByPostingKey(airtime.reference()+":provider-bill-fee")).isTrue();
            assertThatThrownBy(()->ledger.create(customer,UUID.randomUUID().toString(),draft)).hasMessageContaining("awaiting provider activation");
            settings.setBillsApproved(true);
            var failed=ledger.create(customer,UUID.randomUUID().toString(),draft);due(failed.id());ledger.claim(failed.id());
            var failure=new ProviderResult(ProviderResult.State.FAILED,failed.reference(),20000,"NGN","08012345678","CF-failed",null,0);
            ledger.apply(failed.id(),failure);ledger.apply(failed.id(),failure);
            var wallet=wallets.findByUserId(customer).orElseThrow();assertThat(wallet.getLockedKobo()).isZero();assertThat(wallet.getBalanceKobo()).isEqualTo(29900);
        }finally{settings.setMode(MoneySettings.Mode.TEST);settings.setFeesEnabled(false);settings.setBillsApproved(true);settings.setLiveAllowedUsers(Set.of());}
    }
    @Test void liveWithdrawalDailyCapIncludesFeesAndConcurrentReservations() throws Exception {
        UUID customer=saver(0),reviewer=saver(0);verifiedEmail(customer);
        var admin=users.findById(reviewer).orElseThrow();admin.setRole(UserRole.ADMIN);users.saveAndFlush(admin);
        customerReviews.review(reviewer,customer,"TestPassword1!","live-limit-fixture",true,true,true,
            "DOCUMENT_REVIEW","Reviewer checked genuine identity evidence for limit testing.","LIVE");
        settings.setMode(MoneySettings.Mode.LIVE);settings.setFeesEnabled(true);settings.setLiveAllowedUsers(Set.of(customer));
        long oldLimit=settings.getLiveDailyLimitKobo();
        try {
            var funding=ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.DEPOSIT,100000,500));
            due(funding.id());ledger.claim(funding.id());ledger.apply(funding.id(),result(funding,ProviderResult.State.SUCCESS,100500));
            settings.setLiveDailyLimitKobo(60000);
            ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.WITHDRAWAL,50000,250));
            assertThatThrownBy(()->ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.WITHDRAWAL,10000,50)))
                .hasMessageContaining("limit exceeded");
            assertThat(wallets.findByUserId(customer).orElseThrow().getLockedKobo()).isEqualTo(50250);
            settings.setBillsApproved(false);
            assertThatThrownBy(()->ledger.create(customer,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.AIRTIME,10000,50)))
                .hasMessageContaining("awaiting provider activation");
        } finally {settings.setMode(MoneySettings.Mode.TEST);settings.setFeesEnabled(false);settings.setBillsApproved(true);settings.setLiveDailyLimitKobo(oldLimit);settings.setLiveAllowedUsers(Set.of());}
    }
    MoneyDraft feeDraft(MoneyOrder.Kind kind,long amount,long fee) {
        MoneyDraft d=draft(kind,amount);
        return new MoneyDraft(d.kind(),d.amountKobo(),d.currency(),d.destination(),d.serviceId(),d.variationCode(),
            d.groupId(),d.cycleNumber(),d.password(),d.authorizationCode(),fee);
    }
    @Test void depositFeeIsAddedToProviderAmountAndCreditedOnlyOnce() {
        settings.setFeesEnabled(true);
        try {
            UUID user=saver(0);String key=UUID.randomUUID().toString();
            var order=ledger.create(user,key,feeDraft(MoneyOrder.Kind.DEPOSIT,100000,500));
            assertThat(order.providerAmountKobo()).isEqualTo(100500);
            due(order.id());assertThat(ledger.claim(order.id())).isNotNull();
            var verified=new ProviderResult(ProviderResult.State.SUCCESS,order.reference(),100500,"NGN",null,"provider-1",null,100);
            ledger.apply(order.id(),verified);ledger.apply(order.id(),verified);
            assertThat(wallets.findByUserId(user).orElseThrow().getBalanceKobo()).isEqualTo(100000);
            assertThat(journal.existsByPostingKey(order.reference()+":application-fee")).isTrue();
            assertThat(ledger.create(user,key,feeDraft(MoneyOrder.Kind.DEPOSIT,100000,500)).id()).isEqualTo(order.id());
            assertThatThrownBy(()->ledger.create(user,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.DEPOSIT,100000,0))).isInstanceOf(BadRequestException.class);
        } finally {settings.setFeesEnabled(false);}
    }
    @Test void withdrawalFailureReleasesPrincipalAndFeeTogether() {
        settings.setFeesEnabled(true);
        try {
            UUID user=saver(100500);
            var order=ledger.create(user,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.WITHDRAWAL,100000,500));
            assertThat(wallets.findByUserId(user).orElseThrow().getLockedKobo()).isEqualTo(100500);
            due(order.id());ledger.claim(order.id());ledger.apply(order.id(),result(order,ProviderResult.State.FAILED,100000));
            var wallet=wallets.findByUserId(user).orElseThrow();assertThat(wallet.getLockedKobo()).isZero();
            assertThat(wallet.getBalanceKobo()).isEqualTo(100500);
            assertThat(journal.existsByPostingKey(order.reference()+":fee-release")).isTrue();
        } finally {settings.setFeesEnabled(false);}
    }
    @Test void billSuccessDebitsPrincipalPlusFeeAndLeavesProviderAmountUnchanged() {
        settings.setFeesEnabled(true);
        try {
            UUID user=saver(100500);
            var draft=new MoneyDraft(MoneyOrder.Kind.ELECTRICITY,100000,"NGN","1234567890","TESTBILLER","TESTITEM",null,null,"TestPassword1!",null,500L);
            var order=ledger.create(user,UUID.randomUUID().toString(),draft);
            assertThat(order.providerAmountKobo()).isEqualTo(100000);
            due(order.id());ledger.claim(order.id());ledger.apply(order.id(),result(order,ProviderResult.State.SUCCESS,100000));
            var wallet=wallets.findByUserId(user).orElseThrow();assertThat(wallet.getBalanceKobo()).isZero();assertThat(wallet.getLockedKobo()).isZero();
            assertThat(journal.existsByPostingKey(order.reference()+":application-fee")).isTrue();
        } finally {settings.setFeesEnabled(false);}
    }
    @Test void changedSuccessfulPaymentFreezesAccountWithoutInventingRefund() {
        UUID user=saver(0);var order=ledger.create(user,UUID.randomUUID().toString(),draft(MoneyOrder.Kind.DEPOSIT,100000));
        due(order.id());ledger.claim(order.id());ledger.apply(order.id(),result(order,ProviderResult.State.SUCCESS,100000));
        reconciliation.terminal(order.id(),result(order,ProviderResult.State.REVERSED,99999));
        reconciliation.terminal(order.id(),result(order,ProviderResult.State.REVERSED,99999));
        assertThat(users.findById(user).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(wallets.findByUserId(user).orElseThrow().getBalanceKobo()).isEqualTo(100000);
        assertThat(orders.findById(order.id()).orElseThrow().getStatus()).isEqualTo(MoneyOrder.Status.REVIEW);
        assertThat(issues.findTop100ByStatusOrderByCreatedAtAsc("OPEN").stream().filter(i->order.id().equals(i.getOrderId())).count()).isEqualTo(1);
    }
    @Test void verifiedFullFundingReversalUsesCompensatingPostingsOnce() {
        settings.setFeesEnabled(true);
        try {
            UUID user=saver(0);var order=ledger.create(user,UUID.randomUUID().toString(),feeDraft(MoneyOrder.Kind.DEPOSIT,100000,500));
            due(order.id());ledger.claim(order.id());
            var success=new ProviderResult(ProviderResult.State.SUCCESS,order.reference(),100500,"NGN",null,"provider-1",null,0);
            ledger.apply(order.id(),success);
            var reversal=new ProviderResult(ProviderResult.State.REVERSED,order.reference(),100500,"NGN",null,"provider-1",null,0);
            reconciliation.terminal(order.id(),reversal);reconciliation.terminal(order.id(),reversal);
            assertThat(wallets.findByUserId(user).orElseThrow().getBalanceKobo()).isZero();
            assertThat(orders.findById(order.id()).orElseThrow().getStatus()).isEqualTo(MoneyOrder.Status.REVERSED);
            assertThat(journal.existsByPostingKey(order.reference()+":fee-reversal")).isTrue();
        } finally {settings.setFeesEnabled(false);}
    }
    @Test void customerApprovalRequiresIndependentReviewerAndRealEvidence() {
        UUID customer=saver(0),reviewer=saver(0);
        var admin=users.findById(reviewer).orElseThrow();admin.setRole(UserRole.ADMIN);users.saveAndFlush(admin);
        var user=users.findById(customer).orElseThrow();user.setStatus(UserStatus.PENDING_VERIFICATION);users.saveAndFlush(user);
        assertThatThrownBy(()->customerReviews.review(reviewer,customer,"TestPassword1!","case-1",true,true,true,true)).isInstanceOf(BadRequestException.class);
        IdentityCheck evidence=new IdentityCheck();evidence.setUserId(customer);evidence.setStatus("EVIDENCE_RECEIVED");evidence.setProviderMode("TEST");identities.saveAndFlush(evidence);
        assertThatThrownBy(()->customerReviews.review(reviewer,customer,"TestPassword1!","case-1",false,true,true,true)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->customerReviews.review(reviewer,customer,"TestPassword1!","case-no-email",true,true,true,true)).isInstanceOf(BadRequestException.class);
        verifiedEmail(customer);
        customerReviews.review(reviewer,customer,"TestPassword1!","retained-case-1",true,false,true,true);
        var verified=users.findById(customer).orElseThrow();assertThat(verified.isActive()).isTrue();
        assertThat(verified.isEmailVerified()).isTrue();assertThat(verified.isPhoneVerified()).isFalse();
        assertThat(verified.getKycLevel()).isEqualTo(KycStatus.BVN_VERIFIED);
        assertThatThrownBy(()->customerReviews.review(reviewer,reviewer,"TestPassword1!","self-case",true,true,true,true)).isInstanceOf(UnauthorizedException.class);
    }
    UUID saver(long balance) {
        User u=users.saveAndFlush(User.builder().fullName("Test Saver").email(UUID.randomUUID()+"@example.com")
            .phone("+234"+String.format("%010d",Math.abs(UUID.randomUUID().getLeastSignificantBits()%10000000000L)))
            .passwordHash(passwords.encode("TestPassword1!")).status(UserStatus.ACTIVE).build());
        wallets.saveAndFlush(Wallet.builder().user(u).balanceKobo(balance).build()); return u.getId();
    }
    MoneyDraft draft(MoneyOrder.Kind kind,long amount) {
        return new MoneyDraft(kind,amount,"NGN",kind==MoneyOrder.Kind.DEPOSIT?null:"0123456789",
            kind==MoneyOrder.Kind.DEPOSIT?null:"044",null,null,null,"TestPassword1!",null);
    }
    void due(UUID id) { var o=orders.findById(id).orElseThrow();o.setNextCheckAt(Instant.now().minusSeconds(1));orders.saveAndFlush(o); }
    ProviderResult result(MoneyView o,ProviderResult.State state,long amount) {
        return new ProviderResult(state,o.reference(),amount,"NGN",o.destination(),"123",null,0);
    }

    @Autowired GroupMoneyService groupMoney;
    @Autowired com.savex.repository.GroupRepository groupRepository;
    @Test void circlePaysOnlyOnceAfterEveryMemberFundsTheCycle() {
        UUID owner=saver(100000),member=saver(100000),outsider=saver(100000);
        User a=users.findById(owner).orElseThrow(),b=users.findById(member).orElseThrow();
        Group group=Group.builder().creator(a).name("Test circle").contributionKobo(50000).maxMembers((short)2)
            .totalCycles((short)2).startDate(java.time.LocalDate.now().minusMonths(2)).status(GroupStatus.ACTIVE)
            .inviteCode(UUID.randomUUID().toString().substring(0,8)).build();
        group.getMembers().add(GroupMember.builder().group(group).user(a).build());
        group.getMembers().add(GroupMember.builder().group(group).user(b).build());
        group=groupRepository.saveAndFlush(group);UUID groupId=group.getId();
        MoneyDraft cycle1=new MoneyDraft(MoneyOrder.Kind.CONTRIBUTION,50000,"NGN",null,null,null,groupId,1,"TestPassword1!",null);
        assertThatThrownBy(()->groupMoney.contribute(outsider,UUID.randomUUID().toString(),cycle1)).isInstanceOf(NotFoundException.class);
        String key=UUID.randomUUID().toString();var first=groupMoney.contribute(owner,key,cycle1);
        assertThat(wallets.findByUserId(owner).orElseThrow().getBalanceKobo()).isEqualTo(50000);
        assertThat(orders.countByGroupIdAndCycleNumberAndKindAndStatus(groupId,1,MoneyOrder.Kind.PAYOUT,MoneyOrder.Status.SUCCESS)).isZero();
        groupMoney.contribute(member,UUID.randomUUID().toString(),cycle1);
        assertThat(wallets.findByUserId(owner).orElseThrow().getBalanceKobo()).isEqualTo(150000);
        assertThat(groupMoney.contribute(owner,key,cycle1).id()).isEqualTo(first.id());
        assertThatThrownBy(()->groupMoney.contribute(owner,UUID.randomUUID().toString(),cycle1)).isInstanceOf(BadRequestException.class);
        MoneyDraft cycle2=new MoneyDraft(MoneyOrder.Kind.CONTRIBUTION,50000,"NGN",null,null,null,groupId,2,"TestPassword1!",null);
        groupMoney.contribute(owner,UUID.randomUUID().toString(),cycle2);groupMoney.contribute(member,UUID.randomUUID().toString(),cycle2);
        assertThat(wallets.findByUserId(owner).orElseThrow().getBalanceKobo()).isEqualTo(100000);
        assertThat(wallets.findByUserId(member).orElseThrow().getBalanceKobo()).isEqualTo(100000);
        assertThat(groupRepository.findById(groupId).orElseThrow().getStatus()).isEqualTo(GroupStatus.COMPLETED);
        assertThat(orders.countByGroupIdAndCycleNumberAndKindAndStatus(groupId,2,MoneyOrder.Kind.PAYOUT,MoneyOrder.Status.SUCCESS)).isEqualTo(1);
    }
    @Test void mismatchedReferenceAndCurrencyNeverCreditDeposits() {
        for(boolean wrongCurrency:List.of(true,false)) {
            UUID user=saver(0);var order=ledger.create(user,UUID.randomUUID().toString(),draft(MoneyOrder.Kind.DEPOSIT,50000));due(order.id());assertThat(ledger.claim(order.id())).isNotNull();
            ledger.apply(order.id(),new ProviderResult(ProviderResult.State.SUCCESS,wrongCurrency?order.reference():"another-reference",50000,wrongCurrency?"USD":"NGN",null,null,null,0));
            assertThat(wallets.findByUserId(user).orElseThrow().getBalanceKobo()).isZero();
            assertThat(orders.findById(order.id()).orElseThrow().getStatus()).isEqualTo(MoneyOrder.Status.REVIEW);
        }
    }

    @Test void depositOnlyCreditsOnceAfterVerifiedSuccess() {
        UUID user=saver(0);String key=UUID.randomUUID().toString();
        var a=ledger.create(user,key,draft(MoneyOrder.Kind.DEPOSIT,50000));
        var retry=ledger.create(user,key,draft(MoneyOrder.Kind.DEPOSIT,50000)); assertThat(retry.id()).isEqualTo(a.id());
        assertThat(wallets.findByUserId(user).orElseThrow().getBalanceKobo()).isZero();
        due(a.id());assertThat(ledger.claim(a.id()).submit()).isTrue();
        ledger.apply(a.id(),result(a,ProviderResult.State.SUCCESS,50000));
        ledger.apply(a.id(),result(a,ProviderResult.State.SUCCESS,50000));
        assertThat(wallets.findByUserId(user).orElseThrow().getBalanceKobo()).isEqualTo(50000);
        assertThat(journal.findAll().stream().filter(p->p.getPostingKey().equals(a.reference()+":settle")).count()).isEqualTo(1);
        assertThatThrownBy(()->ledger.create(user,key,draft(MoneyOrder.Kind.DEPOSIT,60000))).isInstanceOf(ConflictException.class);
    }
    @Test void timeoutKeepsReserveAndRecoveryNeverResubmits() {
        UUID user=saver(100000);var a=ledger.create(user,UUID.randomUUID().toString(),draft(MoneyOrder.Kind.WITHDRAWAL,60000));
        assertThat(wallets.findByUserId(user).orElseThrow().getAvailableKobo()).isEqualTo(40000);
        due(a.id());assertThat(ledger.claim(a.id()).submit()).isTrue(); ledger.uncertain(a.id());due(a.id());
        assertThat(ledger.claim(a.id()).submit()).isFalse();
        ledger.apply(a.id(),result(a,ProviderResult.State.FAILED,60000));
        ledger.apply(a.id(),result(a,ProviderResult.State.FAILED,60000));
        var w=wallets.findByUserId(user).orElseThrow();assertThat(w.getLockedKobo()).isZero();assertThat(w.getBalanceKobo()).isEqualTo(100000);
    }
    @Test void successfulWithdrawalDebitsOnce() {
        UUID user=saver(100000);var a=ledger.create(user,UUID.randomUUID().toString(),draft(MoneyOrder.Kind.WITHDRAWAL,20000));
        due(a.id());assertThat(ledger.claim(a.id())).isNotNull();ledger.apply(a.id(),result(a,ProviderResult.State.SUCCESS,20000));ledger.apply(a.id(),result(a,ProviderResult.State.SUCCESS,20000));
        var w=wallets.findByUserId(user).orElseThrow();assertThat(w.getBalanceKobo()).isEqualTo(80000);assertThat(w.getLockedKobo()).isZero();
    }
    @Test void mismatchedAmountCannotCreditOrReleaseMoney() {
        UUID user=saver(100000);var a=ledger.create(user,UUID.randomUUID().toString(),draft(MoneyOrder.Kind.WITHDRAWAL,60000));
        due(a.id());assertThat(ledger.claim(a.id())).isNotNull();ledger.apply(a.id(),result(a,ProviderResult.State.SUCCESS,1));
        assertThat(orders.findById(a.id()).orElseThrow().getStatus()).isEqualTo(MoneyOrder.Status.REVIEW);
        var w=wallets.findByUserId(user).orElseThrow();assertThat(w.getBalanceKobo()).isEqualTo(100000);assertThat(w.getLockedKobo()).isEqualTo(60000);
    }
    @Test void concurrentSpendingCannotExceedAvailableFunds() throws Exception {
        UUID user=saver(100000); var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> pay=()->{try{ledger.create(user,UUID.randomUUID().toString(),draft(MoneyOrder.Kind.WITHDRAWAL,70000));return true;}catch(BadRequestException expected){return false;}};
            var results=executor.invokeAll(List.of(pay,pay));
            assertThat(results.stream().filter(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).count()).isEqualTo(1);
            assertThat(wallets.findByUserId(user).orElseThrow().getAvailableKobo()).isEqualTo(30000);
        } finally {executor.shutdownNow();}
    }
    @Test void rejectsUnsupportedCurrencyAndUnauthorisedDebit() {
        UUID user=saver(100000);
        assertThatThrownBy(()->ledger.create(user,UUID.randomUUID().toString(),new MoneyDraft(MoneyOrder.Kind.DEPOSIT,100,"USD",null,null,null,null,null,null,null))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->ledger.create(user,UUID.randomUUID().toString(),new MoneyDraft(MoneyOrder.Kind.WITHDRAWAL,100,"NGN","0123456789","044",null,null,null,"wrong",null))).isInstanceOf(BadRequestException.class);
    }
}


