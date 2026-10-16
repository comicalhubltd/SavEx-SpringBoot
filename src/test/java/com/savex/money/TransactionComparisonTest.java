package com.savex.money;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.savex.money.TransactionComparison.*;
class TransactionComparisonTest {
    MoneyOrder order(MoneyOrder.Status status,String id){var order=new MoneyOrder();order.setReference("savex-test");order.setKind(MoneyOrder.Kind.DEPOSIT);
        order.setProviderMode("TEST");order.setAmountKobo(10000);order.setProviderAmountKobo(10050);order.setStatus(status);order.setProviderReference(id);return order;}
    Attempt attempt(String id,String status,long amount){return new Attempt(id,"savex-test",status,amount,"NGN","",false);}
    @Test void matchesExactSuccessfulAndFailedAttemptsIncludingFundingFee(){
        assertThat(compare(order(MoneyOrder.Status.SUCCESS,"2"),attempt("2","successful",10050),"TEST",1).result()).isEqualTo("MATCHED_SUCCESS");
        assertThat(compare(order(MoneyOrder.Status.FAILED,"1"),attempt("1","failed",10050),"TEST",0).result()).isEqualTo("MATCHED_FAILURE");
        assertThat(compare(order(MoneyOrder.Status.SUCCESS,"2"),attempt("2","successful",10000),"TEST",1).result()).isEqualTo("DETAILS_MISMATCH");
    }
    @Test void keepsOtherFailuresSeparateAndFlagsMultipleSuccesses(){
        assertThat(compare(order(MoneyOrder.Status.SUCCESS,"2"),attempt("1","failed",10050),"TEST",1).result()).isEqualTo("OTHER_FAILED_ATTEMPT");
        assertThat(compare(order(MoneyOrder.Status.SUCCESS,"2"),attempt("2","successful",10050),"TEST",2).result()).isEqualTo("POSSIBLE_DUPLICATE_SUCCESS");
        assertThat(compare(order(MoneyOrder.Status.REVIEW,"1"),attempt("2","successful",10050),"TEST",1).result()).isEqualTo("ATTEMPT_REQUIRES_REVIEW");
    }
    @Test void neverTreatsUnverifiedIdentityOrPendingAsMatched(){
        assertThat(compare(order(MoneyOrder.Status.SUCCESS,null),attempt("2","successful",10050),"TEST",1).result()).isEqualTo("NEEDS_RECONCILIATION");
        assertThat(compare(order(MoneyOrder.Status.PENDING,null),attempt("2","pending",10050),"TEST",0).result()).isEqualTo("PENDING_PROVIDER");
        assertThat(compare(order(MoneyOrder.Status.SUCCESS,"2"),attempt("2","failed",10050),"TEST",0).severity()).isEqualTo("CRITICAL");
    }
    @Test void flagsCrossEnvironmentMissingOrdersCurrencyAndInvalidDetails(){
        assertThat(compare(order(MoneyOrder.Status.SUCCESS,"2"),attempt("2","successful",10050),"LIVE",1).severity()).isEqualTo("CRITICAL");
        assertThat(compare(null,attempt("2","successful",10050),"TEST",1).result()).isEqualTo("PROVIDER_ONLY");
        assertThat(compare(order(MoneyOrder.Status.SUCCESS,"2"),new Attempt("2","savex-test","successful",10050L,"USD","",false),"TEST",1).result()).isEqualTo("DETAILS_MISMATCH");
        assertThat(compare(order(MoneyOrder.Status.SUCCESS,"2"),new Attempt("2","savex-test","successful",null,"NGN","",false),"TEST",1).result()).isEqualTo("INVALID_PROVIDER_DATA");
    }
}
