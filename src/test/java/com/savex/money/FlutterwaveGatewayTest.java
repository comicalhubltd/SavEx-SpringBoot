package com.savex.money;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.*;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;
class FlutterwaveGatewayTest {
    @Test void payoutSubmissionUsesPersistedSourceAndNeverTreatsAcknowledgementAsSuccess() {
        var order=withdrawal();order.setDebitSubaccount("PSATEST123");
        var gateway=responseGateway("{\"status\":\"success\",\"data\":{\"id\":789,\"status\":\"SUCCESSFUL\"}}",request->{
            var outgoing=new org.springframework.mock.http.client.reactive.MockClientHttpRequest(request.method(),request.url());
            request.body().insert(outgoing,new org.springframework.web.reactive.function.BodyInserter.Context(){
                public java.util.List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters(){return ExchangeStrategies.withDefaults().messageWriters();}
                public java.util.Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest(){return java.util.Optional.empty();}
                public java.util.Map<String,Object> hints(){return java.util.Map.of();}
            }).block();
            assertThat(outgoing.getBodyAsString().block()).contains("\"debit_subaccount\":\"PSATEST123\"","\"reference\":\"savex-withdrawal\"");
        });
        assertThat(gateway.submit(order,"fixture@example.com").state()).isEqualTo(ProviderResult.State.PENDING);
    }
    @Test void payoutEndpointsUseCustomerReferenceAndRejectUnknownBalanceCurrency() {
        var gateway=responseGateway("{\"status\":\"success\",\"data\":{\"currency\":\"NGN\",\"available_balance\":1500.25}}",request->assertThat(request.url().getPath()).isEqualTo("/v3/payout-subaccounts/PSATEST123/balances"));
        assertThat(gateway.payoutBalance("PSATEST123")).isEqualTo(150025);
        assertThatThrownBy(()->gateway.payoutBalance("../balances")).hasMessageContaining("Invalid payout wallet reference");
        assertThatThrownBy(()->responseGateway("{\"status\":\"success\",\"data\":{\"currency\":\"USD\",\"available_balance\":100}}",r->{}).payoutBalance("PSATEST123")).hasMessageContaining("currency");
        var json=new com.fasterxml.jackson.databind.ObjectMapper().getNodeFactory();
        assertThat(PayoutWalletStore.fundingFee(json.textNode("2,000.00"))).isEqualTo(200000);
        assertThatThrownBy(()->PayoutWalletStore.fundingFee(json.textNode("2,00.00"))).hasMessageContaining("fee");
    }
    @Test void payoutVerificationRejectsAnotherSourceWallet() {
        var order=withdrawal();order.setProviderReference("789");order.setDebitSubaccount("PSATEST123");
        var gateway=responseGateway("{\"status\":\"success\",\"data\":{\"id\":789,\"debit_subaccount\":\"PSAOTHER\",\"reference\":\"savex-withdrawal\",\"amount\":500,\"currency\":\"NGN\",\"bank_code\":\"044\",\"account_number\":\"0123456789\",\"status\":\"SUCCESSFUL\",\"fee\":10}}",r->{});
        assertThat(gateway.verify(order).state()).isEqualTo(ProviderResult.State.REVIEW);
    }
    @Test void rejectedBillCatalogueReturnsActionableErrorWithoutProviderBody() {
        var settings=new MoneySettings(new StandardEnvironment());settings.setMode(MoneySettings.Mode.LIVE);settings.setBillsApproved(true);
        var gateway=new FlutterwaveGateway(settings,WebClient.builder().exchangeFunction(request->Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
            .header("Content-Type","application/json").body("{\"message\":\"secret-provider-response\"}").build())));
        assertThatThrownBy(()->gateway.billers("AIRTIME")).hasMessageContaining("HTTP 400","Bills API access").hasMessageNotContaining("secret-provider-response");
        assertThatThrownBy(()->gateway.items("BIL001")).hasMessageContaining("packages","HTTP 400").hasMessageNotContaining("secret-provider-response");
    }
    @Test void electricityVerificationReadsTokenAndFeeAndDoesNotInventMissingStatus() {
        var order=new MoneyOrder();order.setKind(MoneyOrder.Kind.ELECTRICITY);order.setReference("savex-bill");order.setProviderReference("CF-fixture");order.setAmountKobo(50000);order.setDestination("1234567890");
        String data="{\"tx_ref\":\"CF-fixture\",\"customer_reference\":\"savex-bill\",\"customer_id\":\"1234567890\",\"status\":\"successful\",\"currency\":\"NGN\",\"amount\":500,\"fee\":10,\"extra\":\"TOKEN123456789\"}";
        var gateway=responseGateway("{\"status\":\"success\",\"data\":"+data+"}",request->assertThat(request.url().getPath()).isEqualTo("/v3/bills/CF-fixture"));
        var result=gateway.verify(order);assertThat(result.state()).isEqualTo(ProviderResult.State.SUCCESS);assertThat(result.rechargeToken()).isEqualTo("TOKEN123456789");assertThat(result.feeKobo()).isEqualTo(1000);assertThat(result.providerReference()).isEqualTo("CF-fixture");
        gateway=responseGateway("{\"status\":\"success\",\"data\":"+data.replace("\"status\":\"successful\",","")+"}",request->{});
        assertThat(gateway.verify(order).state()).isEqualTo(ProviderResult.State.PENDING);
    }
    MoneyOrder withdrawal() {
        var order=new MoneyOrder();order.setKind(MoneyOrder.Kind.WITHDRAWAL);order.setReference("savex-withdrawal");
        order.setAmountKobo(50000);order.setDestination("0123456789");order.setServiceId("044");return order;
    }
    FlutterwaveGateway responseGateway(String body,java.util.function.Consumer<ClientRequest> check) {
        var settings=new MoneySettings(new StandardEnvironment());settings.setMode(MoneySettings.Mode.TEST);settings.setTransfersApproved(true);
        return new FlutterwaveGateway(settings,WebClient.builder().exchangeFunction(request->{check.accept(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").body(body).build());}));
    }
    @Test void withdrawalAcknowledgementNeverSettlesAndSavedIdIsUsedForVerification() {
        var gateway=responseGateway("{\"status\":\"success\",\"data\":{\"id\":789,\"status\":\"SUCCESSFUL\"}}",
            request->assertThat(request.method()).isEqualTo(HttpMethod.POST));
        var order=withdrawal();assertThat(gateway.submit(order,"fixture@example.com").state()).isEqualTo(ProviderResult.State.PENDING);
        order.setProviderReference("789");
        gateway=responseGateway("{\"status\":\"success\",\"data\":{\"id\":789,\"reference\":\"savex-withdrawal\",\"amount\":500,\"currency\":\"NGN\",\"bank_code\":\"044\",\"account_number\":\"0123456789\",\"status\":\"SUCCESSFUL\",\"fee\":10}}",
            request->assertThat(request.url().getPath()).isEqualTo("/v3/transfers/789"));
        var result=gateway.verify(order);assertThat(result.state()).isEqualTo(ProviderResult.State.SUCCESS);
        assertThat(result.amountKobo()).isEqualTo(50000);assertThat(result.feeKobo()).isEqualTo(1000);
    }
    @Test void mismatchedTransferIdentityBankAmountAndCurrencyNeverSettle() {
        String baseline="{\"id\":789,\"reference\":\"savex-withdrawal\",\"amount\":500,\"currency\":\"NGN\",\"bank_code\":\"044\",\"account_number\":\"0123456789\",\"status\":\"SUCCESSFUL\",\"fee\":10}";
        for(String changed:java.util.List.of(baseline.replace("789","790"),baseline.replace("044","058"),baseline.replace("500","501"),baseline.replace("NGN","USD"),baseline.replace("0123456789","0123456788"),baseline.replace("savex-withdrawal","savex-other"))) {
            var order=withdrawal();order.setProviderReference("789");
            assertThat(responseGateway("{\"status\":\"success\",\"data\":"+changed+"}",request->{}).verify(order).state()).isEqualTo(ProviderResult.State.REVIEW);
        }
    }
    @Test void lostSubmissionResponseLooksUpReferenceAndRejectsDuplicateTransfers() {
        var gateway=responseGateway("{\"status\":\"success\",\"data\":[{\"reference\":\"savex-withdrawal\"},{\"reference\":\"savex-withdrawal\"}]}",
            request->assertThat(request.url().getQuery()).isEqualTo("reference=savex-withdrawal"));
        assertThatThrownBy(()->gateway.verify(withdrawal())).hasMessageContaining("Multiple provider transfers");
        assertThat(responseGateway("{\"status\":\"success\",\"data\":[]}",request->{}).verify(withdrawal()).state()).isEqualTo(ProviderResult.State.PENDING);
    }
    @Test void transferFeeUsesExactNgnValuesAndRejectsUnknownFeeTypes() {
        var gateway=responseGateway("{\"status\":\"success\",\"data\":[{\"currency\":\"NGN\",\"fee_type\":\"value\",\"fee\":10.75}]}",
            request->assertThat(request.url().getQuery()).isEqualTo("amount=500.00&currency=NGN&type=account"));
        assertThat(gateway.transferFee(50000)).isEqualTo(1075);
        assertThatThrownBy(()->responseGateway("{\"status\":\"success\",\"data\":[{\"currency\":\"NGN\",\"fee_type\":\"percentage\",\"fee\":1}]}",request->{}).transferFee(50000))
            .hasMessageContaining("Unsupported transfer fee");
    }
    @Test void transactionHistoryRetainsPaginationAndUsesExplicitStatusFilter() {
        MoneySettings settings=new MoneySettings(new StandardEnvironment());settings.setMode(MoneySettings.Mode.TEST);
        var client=WebClient.builder().exchangeFunction(request->{
            assertThat(request.url().toString()).isEqualTo("https://api.flutterwave.com/v3/transactions?from=2026-10-01&to=2026-10-12&page=2&status=failed");
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json")
                .body("{\"status\":\"success\",\"meta\":{\"page_info\":{\"total_pages\":3}},\"data\":[]}").build());
        });
        var page=new FlutterwaveGateway(settings,client).transactionHistory(java.time.LocalDate.parse("2026-10-01"),java.time.LocalDate.parse("2026-10-12"),2,"failed");
        assertThat(page.totalPages()).isEqualTo(3);assertThat(page.rows().isArray()).isTrue();
    }
    @Test void savedAttemptUsesTransactionIdAndRejectsAnotherAttempt() {
        MoneySettings settings=new MoneySettings(new StandardEnvironment());settings.setMode(MoneySettings.Mode.TEST);
        var client=WebClient.builder().exchangeFunction(request->{
            assertThat(request.url().toString()).isEqualTo("https://api.flutterwave.com/v3/transactions/123/verify");
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json")
                .body("{\"status\":\"success\",\"data\":{\"id\":456,\"tx_ref\":\"savex-attempt\",\"status\":\"successful\",\"amount\":100,\"currency\":\"NGN\"}}").build());
        });
        MoneyOrder order=new MoneyOrder();order.setKind(MoneyOrder.Kind.DEPOSIT);order.setReference("savex-attempt");order.setProviderReference("123");
        assertThat(new FlutterwaveGateway(settings,client).verify(order).state()).isEqualTo(ProviderResult.State.REVIEW);
    }
    @Test void verifiedSavedAttemptRetainsItsExactIdentityAndDetails() {
        MoneySettings settings=new MoneySettings(new StandardEnvironment());settings.setMode(MoneySettings.Mode.TEST);
        var client=WebClient.builder().exchangeFunction(request->Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json")
            .body("{\"status\":\"success\",\"data\":{\"id\":123,\"tx_ref\":\"savex-attempt\",\"status\":\"successful\",\"amount\":100.50,\"currency\":\"NGN\"}}").build()));
        MoneyOrder order=new MoneyOrder();order.setKind(MoneyOrder.Kind.DEPOSIT);order.setReference("savex-attempt");order.setProviderReference("123");
        var result=new FlutterwaveGateway(settings,client).verify(order);
        assertThat(result.state()).isEqualTo(ProviderResult.State.SUCCESS);assertThat(result.amountKobo()).isEqualTo(10050);
        assertThat(result.providerReference()).isEqualTo("123");assertThat(result.reference()).isEqualTo("savex-attempt");
    }
    private final ObjectMapper json=new ObjectMapper();
    @Test void convertsMajorUnitsExactly() throws Exception {
        assertThat(FlutterwaveGateway.kobo(json.readTree("\"100.25\""))).isEqualTo(10025);
        assertThatThrownBy(()->FlutterwaveGateway.kobo(json.readTree("\"1.001\""))).isInstanceOf(ArithmeticException.class);
    }
    @Test void submissionCreatesHostedCheckoutWithoutClaimingSuccess() {
        MoneySettings s=new MoneySettings(new StandardEnvironment());s.setMode(MoneySettings.Mode.TEST);
        var client=WebClient.builder().exchangeFunction(request->{
            assertThat(request.url().toString()).isEqualTo("https://api.flutterwave.com/v3/payments");
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json")
                .body("{\"status\":\"success\",\"data\":{\"link\":\"https://checkout.flutterwave.com/v3/hosted/pay/test\"}}").build());
        });
        var gateway=new FlutterwaveGateway(s,client);MoneyOrder o=new MoneyOrder();o.setKind(MoneyOrder.Kind.DEPOSIT);o.setAmountKobo(10000);o.setReference("savex-test");
        var result=gateway.submit(o,"test@example.com");assertThat(result.state()).isEqualTo(ProviderResult.State.PENDING);assertThat(result.checkoutUrl()).startsWith("https://checkout.flutterwave.com/");
    }
    @Test void liveModeCannotBeEnabledBeforeLaunchValidation() {
        MoneySettings s=new MoneySettings(new StandardEnvironment());s.setMode(MoneySettings.Mode.LIVE);s.setFlutterwaveSecret("FLWSECK-fixture-X");s.setWebhookSecret("fixture-secret-long-enough-123456789");
        assertThatThrownBy(s::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("LIVE mode is locked");
    }
}
