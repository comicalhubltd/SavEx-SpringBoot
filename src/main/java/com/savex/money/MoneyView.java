package com.savex.money;
import java.time.Instant;
import java.util.UUID;
public record MoneyView(UUID id,String reference,MoneyOrder.Kind kind,MoneyOrder.Status status,
 long amountKobo,String currency,String destination,String checkoutUrl,Instant createdAt,String rechargeToken,
 long applicationFeeKobo,long totalDebitKobo,long providerAmountKobo,String fundingSource) {
 public static MoneyView from(MoneyOrder o) { return new MoneyView(o.getId(),o.getReference(),o.getKind(),
 o.getStatus(),o.getAmountKobo(),o.getCurrency(),o.getDestination(),o.getCheckoutUrl(),o.getCreatedAt(),
 o.getStatus()==MoneyOrder.Status.SUCCESS && o.getKind()==MoneyOrder.Kind.ELECTRICITY ? o.getRechargeToken() : null,
 o.getApplicationFeeKobo(),o.totalDebit(),o.providerAmount(),o.getDebitSubaccount()==null?"CHECKOUT":"PAYOUT_WALLET"); }
}
