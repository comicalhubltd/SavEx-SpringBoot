package com.savex.money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.util.*;
@Component @EnableScheduling @RequiredArgsConstructor
public class MoneyWorker {
    private final MoneySettings settings;
    private final MoneyOrderRepository orders;
    private final MoneyLedger ledger;
    private final MoneyGateway gateway;
    private final ReconciliationService reconciliation;
    @Scheduled(fixedDelayString="${savex.money.poll-delay-ms:15000}")
    public void poll() {
        if(settings.getMode()==MoneySettings.Mode.OFF || !settings.isWorkerEnabled()) return;
        for(UUID id:orders.due(List.of(MoneyOrder.Status.QUEUED,MoneyOrder.Status.SUBMITTING,MoneyOrder.Status.PENDING,MoneyOrder.Status.SUCCESS,MoneyOrder.Status.FAILED),Instant.now(),settings.getMode().name(),PageRequest.of(0,20))) process(id);
    }
    public void process(UUID id) {
        MoneyLedger.Claim claim=ledger.claim(id);
        if(claim==null) return;
        try {
            if(claim.order().getStatus()!=MoneyOrder.Status.SUCCESS && claim.order().getStatus()!=MoneyOrder.Status.FAILED
                && claim.order().getCreatedAt().isBefore(Instant.now().minusSeconds(86400))) {
                reconciliation.review(id,"PAYMENT_PENDING_OVER_24_HOURS");return;
            }
            ProviderResult result=claim.submit()?gateway.submit(claim.order(),claim.email()):gateway.verify(claim.order());
            if(claim.order().getStatus()==MoneyOrder.Status.SUCCESS || claim.order().getStatus()==MoneyOrder.Status.FAILED) {
                if(result.state()!=ProviderResult.State.PENDING && !(claim.order().getStatus()==MoneyOrder.Status.FAILED && result.state()==ProviderResult.State.FAILED))
                    reconciliation.terminal(id,result);
            } else ledger.apply(id,result);
        } catch(RuntimeException exception) {
            // An HTTP failure can occur after the provider accepted a payment. Keep the reserve and re-query.
            // Never log response bodies: they can contain account and identity data.
            ledger.uncertain(id);
        }
    }
}
