package com.savex.verification;

import com.savex.entity.User;
import com.savex.entity.enums.UserStatus;
import com.savex.repository.UserRepository;
import com.savex.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:emailtests;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=GROUPS","savex.money.mode=OFF","savex.money.worker-enabled=false"})
@ActiveProfiles("dev")
class EmailVerificationTest {
    @Autowired EmailVerificationService service;
    @Autowired EmailChallengeRepository challenges;
    @Autowired UserRepository users;
    @MockitoBean VerificationEmailSender sender;
    UUID customer() {
        return users.saveAndFlush(User.builder().fullName("Email Test").email(UUID.randomUUID()+"@example.com")
            .phone(UUID.randomUUID().toString().substring(0,20)).passwordHash("unused-fixture")
            .status(UserStatus.PENDING_VERIFICATION).build()).getId();
    }
    String send(UUID id) {
        service.send(id);var code=ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq(users.findById(id).orElseThrow().getEmail()),code.capture());return code.getValue();
    }
    void allowResend(UUID id) {
        var c=challenges.findById(id).orElseThrow();c.setSentAt(Instant.now().minusSeconds(61));challenges.saveAndFlush(c);
    }
    @Test void verifiesAfterDatabaseRoundTripAndApprovesOnlyValidAccountWithoutVerifyingIdentity() {
        UUID id=customer();String code=send(id);
        var details=users.findById(id).orElseThrow();details.setPhone("+2348012345678");users.saveAndFlush(details);
        assertThat(challenges.findById(id).orElseThrow().getCodeHash()).doesNotContain(code);
        assertThat(service.check(id,code).verified()).isTrue();
        var user=users.findById(id).orElseThrow();assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.isPhoneVerified()).isFalse();assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getKycLevel()).isEqualTo(com.savex.entity.enums.KycStatus.UNVERIFIED);
        assertThat(user.getEmailAccountApprovedAt()).isNotNull();
        Instant approved=user.getEmailAccountApprovedAt();service.check(id,code);
        assertThat(users.findById(id).orElseThrow().getEmailAccountApprovedAt()).isEqualTo(approved);
        assertThat(challenges.findById(id).orElseThrow().getCodeHash()).isNull();
        service.send(id);verify(sender,times(1)).send(anyString(),anyString());
    }
    @Test void invalidDetailsStayPendingAndRestrictedAccountsNeverReactivate() {
        UUID id=customer();String code=send(id);assertThat(service.check(id,code).verified()).isTrue();
        var user=users.findById(id).orElseThrow();assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.getEmailAccountApprovedAt()).isNull();
        user.setPhone("+2348092345678");users.saveAndFlush(user);service.status(id);
        user=users.findById(id).orElseThrow();assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        user.setStatus(UserStatus.SUSPENDED);users.saveAndFlush(user);
        assertThatThrownBy(()->service.status(id)).isInstanceOf(BadRequestException.class);
        assertThat(users.findById(id).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }
    @Test void concurrentChecksApproveExistingEmailProofOnlyOnce() throws Exception {
        UUID id=customer();String code=send(id);service.check(id,code);
        var user=users.findById(id).orElseThrow();user.setPhone("+2349012345678");users.saveAndFlush(user);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var tasks=new java.util.ArrayList<java.util.concurrent.Callable<StateResult>>();
            for(int i=0;i<8;i++) tasks.add(()->new StateResult(service.status(id).verified()));
            for(var result:executor.invokeAll(tasks)) assertThat(result.get().verified()).isTrue();
        } finally {executor.shutdownNow();}
        user=users.findById(id).orElseThrow();assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        Instant first=user.getEmailAccountApprovedAt();assertThat(first).isNotNull();service.status(id);
        assertThat(users.findById(id).orElseThrow().getEmailAccountApprovedAt()).isEqualTo(first);
        assertThat(user.getKycLevel()).isEqualTo(com.savex.entity.enums.KycStatus.UNVERIFIED);
    }
    record StateResult(boolean verified) {}
    @Test void expiredAndChangedEmailCodesCannotVerify() {
        UUID id=customer();String code=send(id);
        var c=challenges.findById(id).orElseThrow();c.setExpiresAt(Instant.now().minusSeconds(1));challenges.saveAndFlush(c);
        assertThat(service.check(id,code).verified()).isFalse();
        c=challenges.findById(id).orElseThrow();c.setExpiresAt(Instant.now().plusSeconds(600));challenges.saveAndFlush(c);
        var user=users.findById(id).orElseThrow();user.setEmail("changed-"+user.getEmail());users.saveAndFlush(user);
        assertThat(service.check(id,code).verified()).isFalse();
    }
    @Test void codeSurvivesDatabaseTimestampPrecisionChanges() {
        UUID id=customer();String code=send(id);
        var c=challenges.findById(id).orElseThrow();
        c.setSentAt(c.getSentAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        challenges.saveAndFlush(c);
        assertThat(service.check(id,code).verified()).isTrue();
    }
    @Test void fiveFailedAttemptsPersistAndInvalidateCorrectCode() {
        UUID id=customer();String code=send(id);String wrong=code.equals("000000")?"111111":"000000";
        for(int i=0;i<5;i++) assertThat(service.check(id,wrong).verified()).isFalse();
        assertThat(challenges.findById(id).orElseThrow().getAttempts()).isEqualTo(5);
        assertThat(service.check(id,code).verified()).isFalse();
    }
    @Test void resendIsLimitedAndReplacesEarlierCode() {
        UUID id=customer();String first=send(id);
        assertThatThrownBy(()->service.send(id)).isInstanceOf(BadRequestException.class).hasMessageContaining("60 seconds");
        allowResend(id);service.send(id);
        assertThat(service.check(id,first).verified()).isFalse();
        for(int i=0;i<3;i++) {allowResend(id);service.send(id);}
        allowResend(id);
        assertThatThrownBy(()->service.send(id)).isInstanceOf(BadRequestException.class).hasMessageContaining("per hour");
        var c=challenges.findById(id).orElseThrow();c.setWindowStartedAt(Instant.now().minusSeconds(3601));challenges.saveAndFlush(c);
        service.send(id);assertThat(challenges.findById(id).orElseThrow().getSendCount()).isEqualTo(1);
    }
    @Test void failedDeliveryDoesNotConsumeQuotaOrSaveAChallenge() {
        UUID id=customer();doThrow(new BadRequestException("Delivery unavailable")).when(sender).send(anyString(),anyString());
        assertThatThrownBy(()->service.send(id)).isInstanceOf(BadRequestException.class);
        assertThat(challenges.findById(id)).isEmpty();
    }
}
