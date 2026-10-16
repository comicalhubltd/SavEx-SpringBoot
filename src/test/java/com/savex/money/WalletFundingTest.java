package com.savex.money;

import com.savex.repository.WalletRepository;
import com.savex.service.PaymentService;
import com.savex.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletFundingTest {
    @Test void fundingUsesSharedLedgerAndPreservesRetryKey() {
        var ledger=mock(MoneyLedger.class);
        var settings=new MoneySettings(new StandardEnvironment());
        settings.setMode(MoneySettings.Mode.TEST);
        settings.setFeesEnabled(false);
        var service=new PaymentService(mock(WalletRepository.class),ledger,settings);
        var user=UUID.randomUUID();
        String key=UUID.randomUUID().toString();
        service.initiateWalletFunding(user,125,key);
        verify(ledger).create(user,key,new MoneyDraft(MoneyOrder.Kind.DEPOSIT,12500,"NGN",null,null,null,null,null,null,null));
        assertThatThrownBy(()->service.initiateWalletFunding(user,Long.MAX_VALUE,key)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->service.initiateWalletFunding(user,0,key)).isInstanceOf(BadRequestException.class);
        verifyNoMoreInteractions(ledger);
    }
    @Test void disabledPaymentsNeverCreateFunding() {
        var ledger=mock(MoneyLedger.class);
        var service=new PaymentService(mock(WalletRepository.class),ledger,new MoneySettings(new StandardEnvironment()));
        assertThatThrownBy(()->service.initiateWalletFunding(UUID.randomUUID(),100,UUID.randomUUID().toString()))
            .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(ledger);
    }
}
