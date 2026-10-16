package com.savex.money;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savex.verification.AccountAssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WithdrawalPreflightTest {
    final MoneySettings settings=new MoneySettings(new MockEnvironment());
    final AccountAssessmentService assessments=mock(AccountAssessmentService.class);
    final FlutterwaveGateway gateway=mock(FlutterwaveGateway.class);
    final AccountAssessmentService.Assessment assessment=mock(AccountAssessmentService.Assessment.class);
    final WithdrawalPreflight service=new WithdrawalPreflight(settings,assessments,gateway);
    final UUID customer=UUID.randomUUID();
    final ObjectMapper json=new ObjectMapper();
    MoneyDraft draft() {return new MoneyDraft(MoneyOrder.Kind.WITHDRAWAL,50000,"NGN","0123456789","044",null,null,null,"password",null,250L);}
    @BeforeEach void prepare() throws Exception {
        settings.setMode(MoneySettings.Mode.LIVE);settings.setTransfersApproved(true);
        when(assessments.assess(customer)).thenReturn(assessment);
        when(assessment.accountStatus()).thenReturn("ACTIVE");when(assessment.fundingAvailable()).thenReturn(true);
        when(gateway.resolveBank("0123456789","044")).thenReturn(json.readTree("{\"account_number\":\"0123456789\",\"account_name\":\"Fixture Recipient\"}"));
        when(gateway.transferFee(50000)).thenReturn(1000L);
        when(gateway.walletBalances()).thenReturn(json.readTree("[{\"currency\":\"NGN\",\"available_balance\":510}]"));
    }
    @Test void exactPrincipalPlusProviderFeePermitsChecksWithoutCreatingTransfer() {
        service.check(customer,draft());verify(gateway).resolveBank("0123456789","044");verify(gateway).transferFee(50000);verify(gateway).walletBalances();
        verifyNoMoreInteractions(gateway);
    }
    @Test void insufficientProviderFundsAndUnknownBalanceRejectBeforeReservation() throws Exception {
        when(gateway.walletBalances()).thenReturn(json.readTree("[{\"currency\":\"NGN\",\"available_balance\":509.99}]"));
        assertThatThrownBy(()->service.check(customer,draft())).hasMessageContaining("temporarily insufficient");
        when(gateway.walletBalances()).thenReturn(json.readTree("[]"));
        assertThatThrownBy(()->service.check(customer,draft())).hasMessageContaining("could not be confirmed");
    }
    @Test void activationAndAccountRestrictionsPreventProviderCalls() {
        settings.setTransfersApproved(false);assertThatThrownBy(()->service.check(customer,draft())).hasMessageContaining("awaiting provider activation");
        settings.setTransfersApproved(true);when(assessment.fundingAvailable()).thenReturn(false);
        assertThatThrownBy(()->service.check(customer,draft())).hasMessageContaining("approved account");
        when(assessment.fundingAvailable()).thenReturn(true);when(assessment.accountStatus()).thenReturn("SUSPENDED");
        assertThatThrownBy(()->service.check(customer,draft())).hasMessageContaining("active approved");verifyNoInteractions(gateway);
    }
    @Test void unresolvedRecipientAndProviderAccessFailureAreSafeErrors() throws Exception {
        when(gateway.resolveBank("0123456789","044")).thenReturn(json.readTree("{\"account_number\":\"0000000000\",\"account_name\":\"Other\"}"));
        assertThatThrownBy(()->service.check(customer,draft())).hasMessageContaining("recipient bank account");
        when(gateway.resolveBank("0123456789","044")).thenThrow(WebClientResponseException.create(403,"Forbidden",HttpHeaders.EMPTY,"secret-provider-body".getBytes(),null));
        assertThatThrownBy(()->service.check(customer,draft())).hasMessageContaining("server IP allowlist").hasMessageNotContaining("secret-provider-body");
        verify(gateway,never()).transferFee(anyLong());verify(gateway,never()).walletBalances();
    }
    @Test void capabilityMatchesLiveAccountChecksWithoutRequiringCollectionPublicKey() {
        when(assessment.fundingBlockers()).thenReturn(List.of());
        var controller=new MoneyController(settings,mock(MoneyLedger.class),mock(GroupMoneyService.class),gateway,
            mock(BillService.class),mock(ReconciliationService.class),assessments,mock(InlineCheckoutService.class),service,mock(BillPreflight.class),mock(PayoutWalletService.class));
        var principal=org.springframework.security.core.userdetails.User.withUsername(customer.toString()).password("fixture").roles("MEMBER").build();
        assertThat(controller.capabilities(principal).data()).containsEntry("transfers",true).containsEntry("collections",false);
        when(assessment.fundingBlockers()).thenReturn(List.of("Customer is not in the live pilot allowlist"));
        assertThat(controller.capabilities(principal).data()).containsEntry("transfers",false);
    }
}
