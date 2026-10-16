package com.savex.money;
import com.savex.SavExApplication;
import com.savex.entity.*;
import com.savex.entity.enums.*;
import com.savex.repository.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Opt-in restart exercise using a dedicated MariaDB acceptance database, never the live database. */
@EnabledIfEnvironmentVariable(named="SAVEX_PERSISTENCE_TEST",matches="1")
class PersistentRecoveryTest {
    ConfigurableApplicationContext start() { return start(null); }
    ConfigurableApplicationContext start(com.savex.verification.VerificationEmailSender mail) {
        String url=System.getenv("SAVEX_ACCEPTANCE_DB_URL");
        if(url==null || !url.contains("/savex_acceptance?")) throw new IllegalStateException("Dedicated acceptance database required");
        return new SpringApplicationBuilder(SavExApplication.class).profiles("xampp").initializers(context->{
            if(mail!=null) context.getBeanFactory().addBeanPostProcessor(new org.springframework.beans.factory.config.BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean,String name) {
                    return name.equals("verificationEmailSender")?mail:bean;
                }
            });
        }).run(
            "--server.port=0","--spring.datasource.url="+url,"--spring.datasource.username="+System.getenv("DB_USER"),
            "--spring.datasource.password="+System.getenv("DB_PASS"),
            "--savex.jwt.secret=ZGV2LW9ubHktbm90LWZvci1wcm9kdWN0aW9uLXNhdmV4LWtleS0zMi1ieXRlcw==",
            "--savex.money.mode=TEST","--savex.money.flutterwave-secret=FLWSECK_TEST-fixture-X",
            "--savex.money.webhook-secret=fixture-secret-of-at-least-thirty-two-characters","--savex.money.worker-enabled=false");
    }
    @Test void emailCodeSurvivesMariaDbRoundTripAndApplicationRestart() {
        var mail=org.mockito.Mockito.mock(com.savex.verification.VerificationEmailSender.class);
        UUID id;String code;
        try(var context=start(mail)) {
            var users=context.getBean(UserRepository.class);
            User user=users.saveAndFlush(User.builder().fullName("Email Acceptance Fixture")
                .email(UUID.randomUUID()+"@example.test").phone(UUID.randomUUID().toString().substring(0,20))
                .passwordHash("unused-fixture").status(UserStatus.PENDING_VERIFICATION).build());
            id=user.getId();context.getBean(com.savex.verification.EmailVerificationService.class).send(id);
            var captured=org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(mail).send(org.mockito.ArgumentMatchers.eq(user.getEmail()),captured.capture());
            code=captured.getValue();
        }
        try(var context=start(mail)) {
            assertThat(context.getBean(com.savex.verification.EmailVerificationService.class).check(id,code).verified()).isTrue();
            assertThat(context.getBean(UserRepository.class).findById(id).orElseThrow().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        }
    }
    @Test void pendingFundingAndFeePostingSurviveTwoApplicationRestarts() {
        UUID userId,orderId;String reference;
        try(var context=start()) {
            var users=context.getBean(UserRepository.class);var wallets=context.getBean(WalletRepository.class);
            var encoder=context.getBean(PasswordEncoder.class);
            User user=users.saveAndFlush(User.builder().fullName("Acceptance Fixture").email(UUID.randomUUID()+"@example.test")
                .phone("+234"+String.format("%010d",Math.abs(UUID.randomUUID().getLeastSignificantBits()%10000000000L)))
                .passwordHash(encoder.encode("FixturePassword1!")).status(UserStatus.ACTIVE).build());
            userId=user.getId();wallets.saveAndFlush(Wallet.builder().user(user).build());
            var ledger=context.getBean(MoneyLedger.class);
            var order=ledger.create(userId,UUID.randomUUID().toString(),new MoneyDraft(MoneyOrder.Kind.DEPOSIT,100000,"NGN",null,null,null,null,null,null,null,500L));
            orderId=order.id();reference=order.reference();
            var repository=context.getBean(MoneyOrderRepository.class);var saved=repository.findById(orderId).orElseThrow();
            saved.setNextCheckAt(Instant.now().minusSeconds(1));repository.saveAndFlush(saved);
            assertThat(ledger.claim(orderId).submit()).isTrue();
            ledger.apply(orderId,ProviderResult.pending(reference));
        }
        try(var context=start()) {
            var orders=context.getBean(MoneyOrderRepository.class);var pending=orders.findById(orderId).orElseThrow();
            assertThat(pending.getStatus()).isEqualTo(MoneyOrder.Status.PENDING);
            assertThat(pending.getApplicationFeeKobo()).isEqualTo(500);
            pending.setNextCheckAt(Instant.now().minusSeconds(1));orders.saveAndFlush(pending);
            var ledger=context.getBean(MoneyLedger.class);assertThat(ledger.claim(orderId).submit()).isFalse();
            var verified=new ProviderResult(ProviderResult.State.SUCCESS,reference,100500,"NGN",null,"fixture-provider-id",null,0);
            ledger.apply(orderId,verified);ledger.apply(orderId,verified);
        }
        try(var context=start()) {
            assertThat(context.getBean(WalletRepository.class).findByUserId(userId).orElseThrow().getBalanceKobo()).isEqualTo(100000);
            assertThat(context.getBean(MoneyOrderRepository.class).findById(orderId).orElseThrow().getStatus()).isEqualTo(MoneyOrder.Status.SUCCESS);
            assertThat(context.getBean(JournalRepository.class).existsByPostingKey(reference+":application-fee")).isTrue();
            var jdbc=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            assertThatThrownBy(()->jdbc.update("UPDATE journal_postings SET amount_kobo=1 WHERE posting_key=?",reference+":application-fee"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->jdbc.update("DELETE FROM journal_postings WHERE posting_key=?",reference+":application-fee"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }
}
