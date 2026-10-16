package com.savex.money;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savex.entity.Wallet;
import com.savex.repository.WalletRepository;
import com.savex.verification.AccountAssessmentService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantControllerTest {
    final MoneySettings settings=new MoneySettings(new MockEnvironment());
    final MoneyOrderRepository orders=mock(MoneyOrderRepository.class);
    final WalletRepository wallets=mock(WalletRepository.class);
    final AccountAssessmentService assessments=mock(AccountAssessmentService.class);
    final MoneyGateway gateway=mock(MoneyGateway.class);
    final AssistantController controller=new AssistantController(settings,orders,wallets,assessments,gateway);
    final UUID customer=UUID.randomUUID();
    final UserDetails principal=org.springframework.security.core.userdetails.User.withUsername(customer.toString()).password("fixture").roles("MEMBER").build();
    void eligible() {
        settings.setMode(MoneySettings.Mode.LIVE);settings.setTransfersApproved(true);settings.setFlutterwavePublicKey("FLWPUBK-fixture-X");
        var assessment=mock(AccountAssessmentService.Assessment.class);when(assessment.fundingAvailable()).thenReturn(true);
        when(assessments.assess(customer)).thenReturn(assessment);
    }
    @Test void savingsRoundsUpWithoutClaimingInterestOrChangingBalances() {
        var plan=controller.savings(new AssistantController.SavingsRequest(10000,0,3,AssistantController.Frequency.WEEKLY)).data();
        assertThat(plan.contributionKobo()).isEqualTo(3334);assertThat(plan.plannedTotalKobo()).isEqualTo(10002);
        assertThat(controller.savings(new AssistantController.SavingsRequest(10000,11000,4,AssistantController.Frequency.MONTHLY)).data().contributionKobo()).isZero();
        verifyNoInteractions(orders,wallets,gateway,assessments);
    }
    @Test void transactionHelperOnlyExplainsOwnersRecordsAndDoesNotInventProviderReasons() {
        var order=new MoneyOrder();order.setUserId(customer);order.setReference("savex-fixture");order.setKind(MoneyOrder.Kind.WITHDRAWAL);order.setStatus(MoneyOrder.Status.PENDING);order.setAmountKobo(20000);
        when(orders.findByReference(order.getReference())).thenReturn(Optional.of(order));
        var result=controller.troubleshoot(principal,order.getReference()).data();
        assertThat(result.explanation()).contains("not been verified").doesNotContain("IP address");
        assertThat(result.nextStep()).contains("Do not submit a replacement");
        order.setUserId(UUID.randomUUID());assertThatThrownBy(()->controller.troubleshoot(principal,order.getReference())).hasMessageContaining("not found in your account");
        verifyNoInteractions(gateway,wallets,assessments);verify(orders,never()).save(any());
    }
    @Test void fundingProposalQuotesFeesWithoutCreatingAnyOrder() {
        eligible();var result=controller.prepare(principal,new AssistantController.PrepareRequest(MoneyOrder.Kind.DEPOSIT,100000,null,null)).data();
        assertThat(result.applicationFeeKobo()).isEqualTo(500);assertThat(result.totalDebitKobo()).isEqualTo(100500);
        assertThat(result.message()).contains("No funds have been reserved or sent");
        verify(orders,never()).save(any());verifyNoInteractions(wallets,gateway);
    }
    @Test void withdrawalsResolveRecipientButNeverSendAndPreserveAvailableBalance() throws Exception {
        eligible();var wallet=Wallet.builder().balanceKobo(100000).lockedKobo(20000).build();
        when(wallets.findByUserId(customer)).thenReturn(Optional.of(wallet));
        when(gateway.resolveBank("0123456789","044")).thenReturn(new ObjectMapper().readTree("{\"account_name\":\"Fixture Person\",\"account_number\":\"0123456789\"}"));
        var result=controller.prepare(principal,new AssistantController.PrepareRequest(MoneyOrder.Kind.WITHDRAWAL,50000,"0123456789","044")).data();
        assertThat(result.recipientName()).isEqualTo("Fixture Person");assertThat(result.totalDebitKobo()).isEqualTo(50250);
        assertThat(wallet.getAvailableKobo()).isEqualTo(80000);verify(gateway,never()).submit(any(),any());verify(orders,never()).save(any());verify(wallets,never()).save(any());
        assertThatThrownBy(()->controller.prepare(principal,new AssistantController.PrepareRequest(MoneyOrder.Kind.WITHDRAWAL,90000,"0123456789","044"))).hasMessageContaining("Insufficient available funds");
    }
    @Test void disabledServicesPendingDailyUsageAndProviderFailuresBlockPreparationSafely() throws Exception {
        eligible();settings.setTransfersApproved(false);
        assertThatThrownBy(()->controller.prepare(principal,new AssistantController.PrepareRequest(MoneyOrder.Kind.WITHDRAWAL,10000,"0123456789","044"))).hasMessageContaining("awaiting activation");
        settings.setTransfersApproved(true);when(orders.debits(eq(customer),any(),eq(List.of(MoneyOrder.Kind.DEPOSIT)),any())).thenReturn(1_999_900L);
        assertThatThrownBy(()->controller.prepare(principal,new AssistantController.PrepareRequest(MoneyOrder.Kind.DEPOSIT,10000,null,null))).hasMessageContaining("including fees and pending");
        assertThatThrownBy(()->controller.prepare(principal,new AssistantController.PrepareRequest(MoneyOrder.Kind.AIRTIME,10000,null,null))).hasMessageContaining("currently supports");
        when(wallets.findByUserId(customer)).thenReturn(Optional.of(Wallet.builder().balanceKobo(100000).build()));
        when(gateway.resolveBank("0123456789","044")).thenThrow(new RuntimeException("private-provider-response"));
        assertThatThrownBy(()->controller.prepare(principal,new AssistantController.PrepareRequest(MoneyOrder.Kind.WITHDRAWAL,10000,"0123456789","044")))
            .hasMessageContaining("No payment was submitted").hasMessageNotContaining("private-provider-response");
    }
}
