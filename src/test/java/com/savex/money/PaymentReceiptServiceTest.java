package com.savex.money;
import com.savex.exception.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PaymentReceiptServiceTest {
    @Test void receiptsRequireOwnershipAndConfirmedOutcomeAndMaskRecipient() {
        var orders=mock(MoneyOrderRepository.class);var service=new PaymentReceiptService(orders);
        var order=new MoneyOrder();order.setUserId(UUID.randomUUID());order.setKind(MoneyOrder.Kind.WITHDRAWAL);
        order.setAmountKobo(2000000);order.setApplicationFeeKobo(10000);order.setDestination("0123456789");
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        assertThatThrownBy(()->service.receipt(order.getUserId(),order.getId())).isInstanceOf(BadRequestException.class);
        order.setStatus(MoneyOrder.Status.SUCCESS);
        assertThatThrownBy(()->service.receipt(UUID.randomUUID(),order.getId())).isInstanceOf(NotFoundException.class);
        var receipt=service.receipt(order.getUserId(),order.getId());
        assertThat(receipt.maskedDestination()).isEqualTo("••••6789");assertThat(receipt.authorizedTotalKobo()).isEqualTo(2010000);
        assertThat(receipt.electricityToken()).isNull();verify(orders,never()).save(any());
    }
    @Test void reversedElectricityDoesNotExposeAnActiveToken() {
        var orders=mock(MoneyOrderRepository.class);var service=new PaymentReceiptService(orders);
        var order=new MoneyOrder();order.setUserId(UUID.randomUUID());order.setKind(MoneyOrder.Kind.ELECTRICITY);
        order.setStatus(MoneyOrder.Status.REVERSED);order.setRechargeToken("old-token");
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        var receipt=service.receipt(order.getUserId(),order.getId());
        assertThat(receipt.electricityToken()).isNull();assertThat(receipt.note()).contains("reversed");
    }
}
