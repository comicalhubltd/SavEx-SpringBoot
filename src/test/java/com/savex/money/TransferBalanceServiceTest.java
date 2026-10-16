package com.savex.money;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransferBalanceServiceTest {
    final FlutterwaveGateway gateway=mock(FlutterwaveGateway.class);
    final MoneySettings settings=new MoneySettings(new MockEnvironment());
    final TransferBalanceService service=new TransferBalanceService(gateway,settings);
    @Test void exactNgnAmountsAreConvertedOnceAndSharedBetweenCustomersAndAdmin() throws Exception {
        settings.setMode(MoneySettings.Mode.LIVE);
        when(gateway.walletBalances()).thenReturn(new ObjectMapper().readTree("[{\"currency\":\"USD\",\"available_balance\":9,\"ledger_balance\":9},{\"currency\":\"NGN\",\"available_balance\":502.50,\"ledger_balance\":600.01}]"));
        var snapshot=service.current();assertThat(snapshot.availableKobo()).isEqualTo(50250);assertThat(snapshot.ledgerKobo()).isEqualTo(60001);
        var customer=service.customerStatus();assertThat(customer.status()).isEqualTo("FUNDS_AVAILABLE");
        String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(customer);
        assertThat(json).doesNotContain("availableKobo","ledgerKobo","50250","60001");
        verify(gateway,times(1)).walletBalances();
    }
    @Test void providerFailureIsUnavailableNotZeroAndErrorBodyIsNeverExposed() {
        settings.setMode(MoneySettings.Mode.LIVE);when(gateway.walletBalances()).thenThrow(new IllegalStateException("secret-provider-body"));
        var result=service.current();assertThat(result.status()).isEqualTo("UNAVAILABLE");assertThat(result.availableKobo()).isNull();
        assertThat(result.message()).doesNotContain("secret-provider-body");assertThat(service.customerStatus().status()).isEqualTo("UNAVAILABLE");
        verify(gateway,times(1)).walletBalances();
    }
    @Test void offNeverCallsProviderAndChangingEnvironmentInvalidatesCache() throws Exception {
        assertThat(service.current().status()).isEqualTo("OFF");verifyNoInteractions(gateway);
        settings.setMode(MoneySettings.Mode.TEST);
        when(gateway.walletBalances()).thenReturn(new ObjectMapper().readTree("[{\"currency\":\"NGN\",\"available_balance\":0,\"ledger_balance\":0}]"));
        assertThat(service.customerStatus().status()).isEqualTo("NO_FUNDS");
        assertThat(service.current().mode()).isEqualTo("TEST");
    }
    @Test void missingNgnOrMalformedAmountsNeverLookLikeAValidZeroBalance() throws Exception {
        settings.setMode(MoneySettings.Mode.LIVE);
        when(gateway.walletBalances()).thenReturn(new ObjectMapper().readTree("[{\"currency\":\"USD\",\"available_balance\":0,\"ledger_balance\":0}]"));
        assertThat(service.current().availableKobo()).isNull();assertThat(service.current().status()).isEqualTo("UNAVAILABLE");
    }
}
