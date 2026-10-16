package com.savex.money;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class TransactionHistorySyncTest {
    final ObjectMapper json=new ObjectMapper();
    MoneySettings settings(){var settings=new MoneySettings(new StandardEnvironment());settings.setMode(MoneySettings.Mode.TEST);settings.setWorkerEnabled(false);return settings;}
    MoneyOrder order(){var order=new MoneyOrder();order.setReference("savex-test");order.setKind(MoneyOrder.Kind.DEPOSIT);order.setProviderMode("TEST");
        order.setStatus(MoneyOrder.Status.SUCCESS);order.setProviderReference("2");order.setAmountKobo(10000);order.setProviderAmountKobo(10050);return order;}
    void local(MoneyOrderRepository orders,MoneyOrder order){when(orders.historyWindow(anyString(),any(),any(),any(),any())).thenReturn(List.of(order));when(orders.findByReferenceIn(anyCollection())).thenReturn(List.of(order));}
    @Test void importsAllStatusQueriesDeduplicatesAttemptsAndNeverWritesFinancialRecords() throws Exception {
        var gateway=mock(FlutterwaveGateway.class);var orders=mock(MoneyOrderRepository.class);var order=order();local(orders,order);
        JsonNode rows=json.readTree("[{\"id\":1,\"tx_ref\":\"savex-test\",\"status\":\"failed\",\"amount\":100.50,\"currency\":\"NGN\"},{\"id\":2,\"tx_ref\":\"savex-test\",\"status\":\"successful\",\"amount\":100.50,\"currency\":\"NGN\"},{\"id\":3,\"tx_ref\":\"unrelated-business\",\"status\":\"successful\",\"amount\":20,\"currency\":\"NGN\"}]");
        when(gateway.transactionHistory(any(),any(),eq(1),nullable(String.class))).thenReturn(new FlutterwaveGateway.TransactionPage(rows,1));
        var sync=new TransactionHistorySync(settings(),gateway,orders);
        try{sync.syncNow();var report=sync.report(0,"ALL");assertThat(report.complete()).isTrue();assertThat(report.content()).hasSize(2);
            assertThat(report.counts()).containsEntry("MATCHED_SUCCESS",1L).containsEntry("OTHER_FAILED_ATTEMPT",1L);
            assertThat(sync.report(0,"FAILED").content()).hasSize(1);assertThat(sync.report(0,"MATCHED").content()).hasSize(1);
            verify(gateway).transactionHistory(any(),any(),eq(1),isNull());verify(gateway).transactionHistory(any(),any(),eq(1),eq("failed"));
            verify(gateway).transactionHistory(any(),any(),eq(1),eq("successful"));verify(orders,never()).save(any());verify(orders,never()).saveAll(any());
            assertThat(order.getStatus()).isEqualTo(MoneyOrder.Status.SUCCESS);
        }finally{sync.close();}
    }
    @Test void providerFailureNeverLabelsMissingPaymentAsFailed(){
        var gateway=mock(FlutterwaveGateway.class);var orders=mock(MoneyOrderRepository.class);local(orders,order());
        when(gateway.transactionHistory(any(),any(),anyInt(),nullable(String.class))).thenThrow(new IllegalStateException("sensitive provider body"));
        var sync=new TransactionHistorySync(settings(),gateway,orders);
        try{sync.syncNow();var report=sync.report(0,"ALL");assertThat(report.state()).isEqualTo("FAILED");assertThat(report.complete()).isFalse();
            assertThat(report.content()).singleElement().satisfies(row->{assertThat(row.providerStatus()).isEqualTo("NOT_FOUND");assertThat(row.result()).isEqualTo("NOT_FOUND_IN_PARTIAL_IMPORT");});
            assertThat(report.message()).doesNotContain("sensitive");
        }finally{sync.close();}
    }
    @Test void pageLimitIsExplicit() throws Exception {
        var settings=settings();settings.setHistoryMaxPages(1);var gateway=mock(FlutterwaveGateway.class);var orders=mock(MoneyOrderRepository.class);local(orders,order());
        var rows=json.readTree("[{\"id\":2,\"tx_ref\":\"savex-test\",\"status\":\"successful\",\"amount\":100.50,\"currency\":\"NGN\"}]");
        when(gateway.transactionHistory(any(),any(),eq(1),isNull())).thenReturn(new FlutterwaveGateway.TransactionPage(rows,2));
        var sync=new TransactionHistorySync(settings,gateway,orders);
        try{sync.syncNow();assertThat(sync.report(0,"ALL").state()).isEqualTo("PARTIAL");assertThat(sync.report(0,"ALL").complete()).isFalse();}
        finally{sync.close();}
    }
    @Test void unavailableFailedFilterDoesNotDiscardAvailableSuccessHistory() throws Exception {
        var gateway=mock(FlutterwaveGateway.class);var orders=mock(MoneyOrderRepository.class);local(orders,order());
        var rows=json.readTree("[{\"id\":2,\"tx_ref\":\"savex-test\",\"status\":\"successful\",\"amount\":100.50,\"currency\":\"NGN\"}]");
        when(gateway.transactionHistory(any(),any(),eq(1),nullable(String.class))).thenReturn(new FlutterwaveGateway.TransactionPage(rows,1));
        when(gateway.transactionHistory(any(),any(),eq(1),eq("failed"))).thenThrow(org.springframework.web.reactive.function.client.WebClientResponseException.create(403,"Forbidden",null,"private provider content".getBytes(),null));
        var sync=new TransactionHistorySync(settings(),gateway,orders);
        try{sync.syncNow();var report=sync.report(0,"ALL");assertThat(report.state()).isEqualTo("PARTIAL");
            assertThat(report.counts()).containsEntry("MATCHED_SUCCESS",1L);assertThat(report.message()).contains("failed: HTTP 403").doesNotContain("private provider");
        }finally{sync.close();}
    }
    @Test void conflictingAttemptAcrossPagesCannotBeMarkedMatched() throws Exception {
        var gateway=mock(FlutterwaveGateway.class);var orders=mock(MoneyOrderRepository.class);local(orders,order());
        var first=json.readTree("[{\"id\":2,\"tx_ref\":\"savex-test\",\"status\":\"successful\",\"amount\":100.50,\"currency\":\"NGN\"}]");
        var changed=json.readTree("[{\"id\":2,\"tx_ref\":\"savex-test\",\"status\":\"successful\",\"amount\":200,\"currency\":\"NGN\"}]");
        when(gateway.transactionHistory(any(),any(),eq(1),nullable(String.class))).thenReturn(new FlutterwaveGateway.TransactionPage(first,1));
        when(gateway.transactionHistory(any(),any(),eq(1),eq("failed"))).thenReturn(new FlutterwaveGateway.TransactionPage(changed,1));
        var sync=new TransactionHistorySync(settings(),gateway,orders);
        try{sync.syncNow();assertThat(sync.report(0,"ALL").counts()).containsEntry("INVALID_PROVIDER_DATA",1L).doesNotContainKey("MATCHED_SUCCESS");}
        finally{sync.close();}
    }
    @Test void missingSavedAttemptRemainsExplicitWhenAnotherAttemptSharesReference() throws Exception {
        var gateway=mock(FlutterwaveGateway.class);var orders=mock(MoneyOrderRepository.class);local(orders,order());
        var rows=json.readTree("[{\"id\":1,\"tx_ref\":\"savex-test\",\"status\":\"failed\",\"amount\":100.50,\"currency\":\"NGN\"}]");
        when(gateway.transactionHistory(any(),any(),eq(1),nullable(String.class))).thenReturn(new FlutterwaveGateway.TransactionPage(rows,1));
        var sync=new TransactionHistorySync(settings(),gateway,orders);
        try{sync.syncNow();assertThat(sync.report(0,"ALL").counts()).containsEntry("OTHER_FAILED_ATTEMPT",1L).containsEntry("SAVED_ATTEMPT_NOT_FOUND",1L);}
        finally{sync.close();}
    }
}
