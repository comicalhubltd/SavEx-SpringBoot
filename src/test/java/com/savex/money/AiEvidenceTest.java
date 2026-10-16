package com.savex.money;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savex.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiEvidenceTest {
    @Test void collectedEvidenceOmitsCustomerAndProviderIdentifiersAndMarksCoverage() {
        var orders=mock(MoneyOrderRepository.class);var settlements=mock(SettlementMatchRepository.class);
        var receipts=mock(BankSettlementReceiptRepository.class);var sync=mock(TransactionHistorySync.class);
        var wallets=mock(WalletRepository.class);var journal=mock(JournalRepository.class);
        var settings=new MoneySettings(new MockEnvironment());settings.setMode(MoneySettings.Mode.LIVE);
        var order=new MoneyOrder();order.setUserId(UUID.randomUUID());order.setReference("savex-private-reference");order.setProviderReference("123456789");
        order.setProviderMode("LIVE");order.setKind(MoneyOrder.Kind.WITHDRAWAL);order.setAmountKobo(20000);order.setDestination("0123456789");
        when(orders.findByReference(order.getReference())).thenReturn(Optional.of(order));
        when(settlements.findByOrderId(order.getId())).thenReturn(Optional.empty());when(wallets.findByUserId(order.getUserId())).thenReturn(Optional.empty());
        when(sync.snapshot()).thenReturn(new TransactionHistorySync.Snapshot("LIVE","PARTIAL",null,null,null,null,1,false,"403",List.of()));
        var data=new AiEvidence(orders,settings,settlements,receipts,sync,wallets,journal,new ObjectMapper()).collect(order.getReference());
        assertThat(data.toString()).doesNotContain("0123456789","123456789",order.getUserId().toString(),order.getReference());
        assertThat(data.path("historyImport").path("complete").asBoolean()).isFalse();
        assertThat(data.path("limitations").asText()).contains("No live Flutterwave call");
        assertThat(data.path("bankReceiptRecorded").asBoolean()).isFalse();verify(orders,never()).save(any());
    }
}
