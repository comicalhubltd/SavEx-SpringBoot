package com.savex.money;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savex.verification.AccountAssessmentService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BillPreflightTest {
    @Test void merchantFundsMustCoverBillAndProviderChargeWithoutChangingCustomerQuote() throws Exception {
        var settings=new MoneySettings(new MockEnvironment());settings.setMode(MoneySettings.Mode.LIVE);settings.setBillsApproved(true);
        var assessments=mock(AccountAssessmentService.class);var bills=mock(BillService.class);var gateway=mock(FlutterwaveGateway.class);
        UUID user=UUID.randomUUID();var account=mock(AccountAssessmentService.Assessment.class);when(account.accountStatus()).thenReturn("ACTIVE");when(account.fundingAvailable()).thenReturn(true);when(assessments.assess(user)).thenReturn(account);
        var draft=new MoneyDraft(MoneyOrder.Kind.ELECTRICITY,50000,"NGN","1234567890","BIL001","UB001",null,null,"fixture",null,250L);
        when(bills.validate(draft)).thenReturn(new BillService.Quote("Fixture Customer","1234567890",50000,1000));
        var json=new ObjectMapper();when(gateway.walletBalances()).thenReturn(json.readTree("[{\"currency\":\"NGN\",\"available_balance\":509.99}]"));
        var service=new BillPreflight(settings,assessments,bills,gateway);
        assertThatThrownBy(()->service.check(user,draft)).hasMessageContaining("provider funds are insufficient");
        when(gateway.walletBalances()).thenReturn(json.readTree("[{\"currency\":\"NGN\",\"available_balance\":510}]"));service.check(user,draft);
        settings.setBillsApproved(false);clearInvocations(gateway,bills);
        assertThatThrownBy(()->service.check(user,draft)).hasMessageContaining("awaiting provider activation");verifyNoInteractions(gateway,bills);
    }
}
