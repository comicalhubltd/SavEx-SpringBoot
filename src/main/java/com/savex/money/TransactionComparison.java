package com.savex.money;
import java.util.*;

/** Report-only comparison. No ledger or account mutations are allowed here. */
public final class TransactionComparison {
    private TransactionComparison() {}
    public record Attempt(String id,String reference,String status,Long amountKobo,String currency,String createdAt,boolean inconsistent) {}
    public record Row(UUID orderId,UUID customerId,String reference,String providerId,String savedProviderId,
        String savexStatus,String providerStatus,Long expectedKobo,Long providerKobo,String expectedCurrency,String currency,
        String result,String severity,String finding) {}
    static Row row(MoneyOrder order,Attempt attempt,String result,String severity,String finding) {
        return new Row(order==null?null:order.getId(),order==null?null:order.getUserId(),attempt.reference(),attempt.id(),
            order==null?null:order.getProviderReference(),order==null?"NOT_FOUND":order.getStatus().name(),attempt.status(),
            order==null?null:order.providerAmount(),attempt.amountKobo(),order==null?null:order.getCurrency(),attempt.currency(),result,severity,finding);
    }
    public static Row compare(MoneyOrder order,Attempt attempt,String mode,long successfulAttempts) {
        if(attempt.inconsistent() || attempt.amountKobo()==null || attempt.amountKobo()<0 || attempt.currency()==null || attempt.currency().isBlank())
            return row(order,attempt,"INVALID_PROVIDER_DATA","CRITICAL","Missing, invalid or conflicting provider details; investigate before using this record.");
        if(order==null)return row(null,attempt,"PROVIDER_ONLY","WARNING","SavEx-prefixed provider record has no corresponding SavEx order.");
        if(!mode.equals(order.getProviderMode()) || order.getKind()!=MoneyOrder.Kind.DEPOSIT)
            return row(order,attempt,"ORDER_TYPE_OR_ENVIRONMENT_MISMATCH","CRITICAL","The linked order belongs to another service or provider environment.");
        if(order.providerAmount()!=attempt.amountKobo() || !order.getCurrency().equals(attempt.currency()))
            return row(order,attempt,"DETAILS_MISMATCH","CRITICAL","Provider amount or currency differs from the saved order total, including the SavEx funding fee.");
        if("successful".equals(attempt.status()) && successfulAttempts>1)
            return row(order,attempt,"POSSIBLE_DUPLICATE_SUCCESS","CRITICAL","More than one successful transaction ID shares this reference. Each attempt requires investigation.");
        String saved=order.getProviderReference();boolean different=saved!=null && !saved.isBlank() && !saved.equals(attempt.id());
        if(different) {
            if("failed".equals(attempt.status()))return row(order,attempt,"OTHER_FAILED_ATTEMPT","INFO","A separate failed attempt shares this reference; it does not overturn another successful attempt.");
            return row(order,attempt,"ATTEMPT_REQUIRES_REVIEW","WARNING","This transaction ID differs from the attempt saved on the order.");
        }
        String status=order.getStatus().name();
        if("successful".equals(attempt.status())) {
            if("SUCCESS".equals(status) && saved!=null && !saved.isBlank())return row(order,attempt,"MATCHED_SUCCESS","MATCHED","Reference, saved transaction ID, amount, currency and successful status match.");
            return row(order,attempt,"NEEDS_RECONCILIATION","WARNING","Flutterwave reports success; SavEx has not recorded the same confirmed successful attempt.");
        }
        if("SUCCESS".equals(status) && Set.of("failed","reversed","cancelled").contains(attempt.status()))
            return row(order,attempt,"CRITICAL_DISCREPANCY","CRITICAL","SavEx reports success but this saved provider attempt failed, was cancelled or was reversed.");
        if("failed".equals(attempt.status()) && "FAILED".equals(status) && saved!=null && !saved.isBlank())
            return row(order,attempt,"MATCHED_FAILURE","MATCHED","The saved failed attempt matches the reference, transaction ID, amount and currency.");
        if("reversed".equals(attempt.status()) && "REVERSED".equals(status) && saved!=null && !saved.isBlank())
            return row(order,attempt,"MATCHED_REVERSAL","MATCHED","The saved reversed attempt matches; confirm any required bank/settlement evidence separately.");
        if(Set.of("pending","new","processing").contains(attempt.status()))
            return row(order,attempt,"PENDING_PROVIDER","WARNING","Provider outcome is not final; do not classify it as a failed payment.");
        return row(order,attempt,"STATUS_REQUIRES_REVIEW","WARNING","Statuses or saved attempt identity are not aligned; no balances have been changed.");
    }
}
