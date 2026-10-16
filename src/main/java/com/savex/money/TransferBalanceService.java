package com.savex.money;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.time.Instant;

/** Read-only merchant balance snapshot; no customer wallet changes or transfers. */
@Service @RequiredArgsConstructor
public class TransferBalanceService {
    private final FlutterwaveGateway gateway;
    private final MoneySettings settings;
    private Snapshot cached;
    public record Snapshot(String mode,String currency,String status,Long availableKobo,Long ledgerKobo,
                           Instant checkedAt,String message) {}
    public record CustomerStatus(String mode,String currency,String status,Instant checkedAt,String message) {}
    public CustomerStatus customerStatus() {
        Snapshot result=current();
        String status=result.status();String message=result.message();
        if("AVAILABLE".equals(status)) {
            status=result.availableKobo()>0?"FUNDS_AVAILABLE":"NO_FUNDS";
            message=result.availableKobo()>0?"Provider funds are available. A withdrawal still needs enough funds for its amount and provider fee, plus account and service approval."
                :"Provider withdrawal funds are currently unavailable. Contact support.";
        }
        return new CustomerStatus(result.mode(),result.currency(),status,result.checkedAt(),message);
    }
    public synchronized Snapshot current() {
        Instant now=Instant.now();String mode=settings.getMode().name();
        if(cached!=null && cached.mode().equals(mode) && now.isBefore(cached.checkedAt().plusSeconds(30)))return cached;
        if(settings.getMode()==MoneySettings.Mode.OFF)
            return cached=new Snapshot(mode,"NGN","OFF",null,null,now,"Payments are OFF. No provider balance was requested.");
        try {
            JsonNode rows=gateway.walletBalances();
            if(!rows.isArray())throw new IllegalArgumentException("Unexpected balance response");
            JsonNode ngn=null;
            for(JsonNode row:rows)if("NGN".equals(row.path("currency").asText())) {
                if(ngn!=null)throw new IllegalArgumentException("Duplicate currency");ngn=row;
            }
            if(ngn==null)throw new IllegalArgumentException("NGN balance not returned");
            long available=FlutterwaveGateway.kobo(ngn.path("available_balance"));
            long ledger=FlutterwaveGateway.kobo(ngn.path("ledger_balance"));
            return cached=new Snapshot(mode,"NGN","AVAILABLE",available,ledger,Instant.now(),
                "Provider balance snapshot; not a customer wallet balance or transfer-permission confirmation. Provider transfer fees must also be covered.");
        } catch(RuntimeException exception) {
            String message=exception instanceof WebClientResponseException response && response.getStatusCode().value()==403
                ?"Flutterwave denied the balance check (403). Contact support to check API access and IP restrictions."
                :"Flutterwave balance could not be confirmed. Try again shortly; unavailable does not mean zero.";
            return cached=new Snapshot(mode,"NGN","UNAVAILABLE",null,null,Instant.now(),message);
        }
    }
}
