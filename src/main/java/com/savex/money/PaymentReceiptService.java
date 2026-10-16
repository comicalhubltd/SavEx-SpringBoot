package com.savex.money;
import com.savex.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class PaymentReceiptService {
    private final MoneyOrderRepository orders;
    public record Receipt(UUID receiptId,String reference,String providerReference,String environment,
            MoneyOrder.Kind kind,MoneyOrder.Status status,String currency,long amountKobo,long serviceFeeKobo,
            long authorizedTotalKobo,String maskedDestination,String electricityToken,Instant createdAt,
            Instant updatedAt,String note,String fundingSource,long providerAmountKobo,long providerFeeKobo) {}
    @Transactional(readOnly=true)
    public Receipt receipt(UUID customerId,UUID orderId) {
        var order=orders.findById(orderId).filter(o->customerId.equals(o.getUserId()))
            .orElseThrow(()->new NotFoundException("Payment not found"));
        if(order.getStatus()!=MoneyOrder.Status.SUCCESS && order.getStatus()!=MoneyOrder.Status.REVERSED)
            throw new BadRequestException("A receipt is available after a payment is confirmed or reversed");
        String destination=order.getDestination();
        String masked=destination==null || destination.length()<4?null:"••••"+destination.substring(destination.length()-4);
        return new Receipt(order.getId(),order.getReference(),order.getProviderReference(),order.getProviderMode(),
            order.getKind(),order.getStatus(),order.getCurrency(),order.getAmountKobo(),order.getApplicationFeeKobo(),
            order.totalDebit(),masked,order.getStatus()==MoneyOrder.Status.SUCCESS && order.getKind()==MoneyOrder.Kind.ELECTRICITY?order.getRechargeToken():null,
            order.getCreatedAt(),order.getUpdatedAt(),order.getStatus()==MoneyOrder.Status.REVERSED?
                "This payment was reversed. This receipt is not proof of an active wallet credit.":
                order.getDebitSubaccount()!=null && order.getKind()==MoneyOrder.Kind.DEPOSIT?
                "Personal funding account deposit. Amount is the verified transfer less its provider funding fee; no SavEx deposit fee was charged. This is separate from checkout funds and is not a VAT invoice.":
                "Confirmed SavEx payment. Authorized total includes the SavEx service fee. Provider settlement charges are accounted for separately; this is not a VAT invoice.",
            order.getDebitSubaccount()==null?"CHECKOUT":"PAYOUT_WALLET",order.providerAmount(),order.getProviderFeeKobo());
    }
}
