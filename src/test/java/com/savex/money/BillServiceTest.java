package com.savex.money;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savex.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BillServiceTest {
    private final MoneyGateway gateway=mock(MoneyGateway.class);
    private final BillService bills=new BillService(gateway);
    private final ObjectMapper json=new ObjectMapper();
    private MoneyDraft draft(long amount) {
        return new MoneyDraft(MoneyOrder.Kind.ELECTRICITY,amount,"NGN","12345678901","BIL001","UB001",null,null,null,null);
    }
    private void fixture(String responseCode,String customer,int minimum,int maximum,int fee) throws Exception {
        when(gateway.billers("UTILITYBILLS")).thenReturn(json.readTree("[{\"biller_code\":\"BIL001\"}]"));
        when(gateway.items("BIL001")).thenReturn(json.readTree("[{\"item_code\":\"UB001\",\"amount\":0}]"));
        when(gateway.validateBill("UB001","12345678901")).thenReturn(json.readTree(
            "{\"response_code\":\""+responseCode+"\",\"customer\":\""+customer+"\",\"biller_code\":\"BIL001\",\"product_code\":\"UB001\",\"name\":\"Fixture Customer\",\"minimum\":"+minimum+",\"maximum\":"+maximum+",\"fee\":"+fee+"}"));
    }
    @Test void returnsProviderValidatedCustomer() throws Exception {
        fixture("00","12345678901",100,5000,0);
        assertThat(bills.validate(draft(20000)).customerName()).isEqualTo("Fixture Customer");
    }
    @Test void rejectsInvalidCustomerAndMismatchedAccount() throws Exception {
        fixture("01","12345678901",0,0,0);
        assertThatThrownBy(()->bills.validate(draft(20000))).isInstanceOf(BadRequestException.class);
        fixture("00","99999999999",0,0,0);
        assertThatThrownBy(()->bills.validate(draft(20000))).isInstanceOf(BadRequestException.class);
    }
    @Test void rejectsAmountsOutsideProviderLimitsAndQuotesMerchantBorneFees() throws Exception {
        fixture("00","12345678901",500,1000,0);
        assertThatThrownBy(()->bills.validate(draft(20000))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->bills.validate(draft(200000))).isInstanceOf(BadRequestException.class);
        fixture("00","12345678901",0,0,10);
        assertThat(bills.validate(draft(20000)).providerFeeKobo()).isEqualTo(1000);
    }
    @Test void rejectsForeignCatalogAndChangedPackagePrice() throws Exception {
        fixture("00","12345678901",0,0,0);
        when(gateway.billers("UTILITYBILLS")).thenReturn(json.readTree("[{\"biller_code\":\"OTHER\"}]"));
        assertThatThrownBy(()->bills.validate(draft(20000))).isInstanceOf(BadRequestException.class);
        verify(gateway,never()).validateBill(anyString(),anyString());
        fixture("00","12345678901",0,0,0);
        when(gateway.items("BIL001")).thenReturn(json.readTree("[{\"item_code\":\"UB001\",\"amount\":300}]"));
        assertThatThrownBy(()->bills.validate(draft(20000))).isInstanceOf(BadRequestException.class).hasMessageContaining("price");
    }
    @Test void tokenIsOnlyVisibleForSuccessfulElectricityOrder() {
        MoneyOrder order=new MoneyOrder();order.setKind(MoneyOrder.Kind.ELECTRICITY);order.setRechargeToken("fixture-token");
        for(var state:new MoneyOrder.Status[]{MoneyOrder.Status.PENDING,MoneyOrder.Status.REVIEW,MoneyOrder.Status.FAILED}) {
            order.setStatus(state);assertThat(MoneyView.from(order).rechargeToken()).isNull();
        }
        order.setStatus(MoneyOrder.Status.SUCCESS);assertThat(MoneyView.from(order).rechargeToken()).isEqualTo("fixture-token");
        order.setKind(MoneyOrder.Kind.DATA);assertThat(MoneyView.from(order).rechargeToken()).isNull();
    }
    @Test void mobilePackagesValidateNetworkPriceAndNigerianPhoneWithoutCustomerLookup() throws Exception {
        when(gateway.billers("MOBILEDATA")).thenReturn(json.readTree("[{\"biller_code\":\"BIL001\"}]"));
        when(gateway.items("BIL001")).thenReturn(json.readTree("[{\"item_code\":\"MD001\",\"amount\":500,\"fee\":5}]"));
        var valid=new MoneyDraft(MoneyOrder.Kind.DATA,50000,"NGN","08012345678","BIL001","MD001",null,null,null,null);
        assertThat(bills.validate(valid).providerFeeKobo()).isEqualTo(500);
        assertThatThrownBy(()->bills.validate(new MoneyDraft(MoneyOrder.Kind.DATA,40000,"NGN","08012345678","BIL001","MD001",null,null,null,null))).hasMessageContaining("price");
        assertThatThrownBy(()->bills.validate(new MoneyDraft(MoneyOrder.Kind.DATA,50000,"NGN","1234","BIL001","MD001",null,null,null,null))).hasMessageContaining("Nigerian mobile");
        verify(gateway,never()).validateBill(anyString(),anyString());
    }
}
