package com.savex.money;
import com.fasterxml.jackson.databind.JsonNode;
import com.savex.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
@Component
public class FlutterwaveGateway implements MoneyGateway {
    private final MoneySettings settings;
    private final WebClient http;
    private final WebClient identityHttp;
    public FlutterwaveGateway(MoneySettings settings, WebClient.Builder builder) {
        this.settings=settings;
        this.http=builder.baseUrl("https://api.flutterwave.com/v3")
            .defaultHeader("Authorization","Bearer "+settings.getFlutterwaveSecret())
            .codecs(c -> c.defaultCodecs().maxInMemorySize(1024*1024)).build();
        this.identityHttp=builder.clone().baseUrl("https://api.flutterwave.com/v3")
            .defaultHeader("Authorization","Bearer "+settings.getIdentitySecret())
            .codecs(c->c.defaultCodecs().maxInMemorySize(1024*1024)).build();
    }
    public JsonNode initiateBvn(String bvn,String firstname,String lastname) {
        settings.requireIdentity();
        JsonNode response=identityHttp.post().uri("/bvn/verifications").bodyValue(Map.of("bvn",bvn,"firstname",firstname,"lastname",lastname,
            "redirect_url",settings.getPublicUrl()+"/?identity=returned")).retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(25));
        // Flutterwave documents a boolean success field when consent was granted previously.
        if(response!=null && response.path("success").asBoolean(false) && response.path("data").isObject()) return response.path("data");
        return data(response);
    }
    public JsonNode retrieveBvn(String reference) {
        settings.requireIdentity();
        return data(identityHttp.get().uri("/bvn/verifications/{ref}",reference).retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(20)));
    }
    private JsonNode get(String path, Object... variables) {
        settings.requireEnabled();
        return http.get().uri(path,variables).retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(20));
    }
    private JsonNode post(String path, Object body) {
        settings.requireEnabled();
        return http.post().uri(path).bodyValue(body).retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(25));
    }
    private JsonNode data(JsonNode response) {
        if (response==null || !"success".equals(response.path("status").asText()) || response.path("data").isMissingNode())
            throw new IllegalStateException("Provider response requires reconciliation");
        return response.path("data");
    }
    public static long kobo(JsonNode amount) {
        if (amount.isMissingNode() || amount.isNull()) throw new IllegalArgumentException("Missing provider amount");
        return new BigDecimal(amount.asText()).movePointRight(2).longValueExact();
    }
    public JsonNode billers(String category) {
        settings.requireBills();
        if (!Set.of("AIRTIME","MOBILEDATA","UTILITYBILLS","CABLEBILLS","INTSERVICE").contains(category)) throw new BadRequestException("Unsupported bill category");
        try {
            JsonNode rows=data(get("/bills/{category}/billers?country=NG",category));
            if(!rows.isArray())throw new BadRequestException("Flutterwave returned an invalid bill-provider catalogue. Contact support.");
            return rows;
        }catch(org.springframework.web.reactive.function.client.WebClientResponseException error){
            throw new BadRequestException("Flutterwave could not load bill providers (HTTP "+error.getStatusCode().value()+"). Ask Flutterwave support to confirm Bills API access and the server IP allowlist. Enabling bills in SavEx does not activate provider access.");
        }
    }
    public JsonNode items(String biller) {
        settings.requireBills(); code(biller);
        try {
            JsonNode rows=data(get("/billers/{biller}/items",biller));
            if(!rows.isArray())throw new BadRequestException("Flutterwave returned an invalid package catalogue. Contact support.");
            return rows;
        }catch(org.springframework.web.reactive.function.client.WebClientResponseException error){
            throw new BadRequestException("Flutterwave could not load this provider's packages (HTTP "+error.getStatusCode().value()+"). Contact support to confirm bill-service access.");
        }
    }
    public JsonNode validateBill(String itemCode,String customer) {
        settings.requireBills(); code(itemCode);
        if(customer==null || !customer.matches("[A-Za-z0-9@._+\\-]{4,64}")) throw new BadRequestException("Invalid customer account");
        return data(get("/bill-items/{item}/validate?customer={customer}",itemCode,customer));
    }
    public JsonNode banks() { return data(get("/banks/NG")); }
    public JsonNode walletBalances() { return data(get("/balances")); }
    public JsonNode createPayoutWallet(String name,String email) {
        settings.requirePayoutWallets();
        return data(post("/payout-subaccounts",Map.of("account_name",name,"email",email,"country","NG")));
    }
    private void payoutReference(String ref) {
        if(ref==null || !ref.matches("PSA[A-Za-z0-9]{1,77}"))throw new BadRequestException("Invalid payout wallet reference");
    }
    public JsonNode payoutWallet(String ref) {payoutReference(ref);return data(get("/payout-subaccounts/{ref}",ref));}
    public JsonNode payoutAccount(String ref) {payoutReference(ref);return data(get("/payout-subaccounts/{ref}/static-account?currency=NGN&verbose=0",ref));}
    public long payoutBalance(String ref) {
        payoutReference(ref);
        JsonNode row=data(get("/payout-subaccounts/{ref}/balances?currency=NGN",ref));
        if(row.isArray()) {
            JsonNode found=null;
            for(JsonNode candidate:row) if("NGN".equals(candidate.path("currency").asText())) {
                if(found!=null)throw new IllegalStateException("Duplicate payout balance"); found=candidate;
            }
            row=found;
        }
        if(row==null || !"NGN".equals(row.path("currency").asText()))throw new IllegalStateException("Invalid payout balance currency");
        long value=kobo(row.path("available_balance"));
        if(value<0)throw new IllegalStateException("Negative payout balance");return value;
    }
    public JsonNode payoutTransactions(String ref,java.time.LocalDate from,java.time.LocalDate to,int page) {
        payoutReference(ref);
        if(page<1 || page>50 || from==null || to==null || from.isAfter(to))throw new BadRequestException("Invalid wallet history range");
        JsonNode result=data(get("/payout-subaccounts/{ref}/transactions?from={from}&to={to}&currency=NGN&page={page}&fetch_limit=100",ref,from,to,page));
        JsonNode rows=result.isArray()?result:result.path("transactions");
        if(!rows.isArray())throw new IllegalStateException("Unknown payout history format");return rows;
    }
    public JsonNode payoutFunding(String id) {
        if(id==null || !id.matches("[0-9]{1,20}"))throw new BadRequestException("Invalid funding transfer");
        JsonNode row=data(get("/transfers/{id}",id));
        if(!id.equals(row.path("id").asText()))throw new IllegalStateException("Funding transfer identity mismatch");return row;
    }
    public long transferFee(long amountKobo) {
        JsonNode rows=data(get("/transfers/fee?amount={amount}&currency=NGN&type=account",BigDecimal.valueOf(amountKobo,2)));
        if(!rows.isArray() || rows.size()!=1) throw new IllegalStateException("Invalid transfer fee response");
        JsonNode fee=rows.get(0);
        if(!"NGN".equals(fee.path("currency").asText()) || !"value".equals(fee.path("fee_type").asText()))
            throw new IllegalStateException("Unsupported transfer fee");
        long value=kobo(fee.path("fee"));
        if(value<0) throw new IllegalStateException("Invalid transfer fee");
        return value;
    }
    public record TransactionPage(JsonNode rows,int totalPages) {}
    public TransactionPage transactionHistory(java.time.LocalDate from,java.time.LocalDate to,int page,String status) {
        if(from==null || to==null || from.isAfter(to) || page<1 || page>1000
            || (status!=null && !Set.of("successful","failed").contains(status)))
            throw new BadRequestException("Invalid transaction history lookup");
        JsonNode response=status==null?get("/transactions?from={from}&to={to}&page={page}",from,to,page):
            get("/transactions?from={from}&to={to}&page={page}&status={status}",from,to,page,status);
        JsonNode rows=data(response);
        if(!rows.isArray())throw new IllegalStateException("Invalid transaction history response");
        JsonNode pages=response.path("meta").path("page_info").path("total_pages");
        int total=pages.isMissingNode() || pages.isNull()?-1:pages.asInt(-1);
        if(total < -1 || (rows.size()>0 && total==0))throw new IllegalStateException("Invalid transaction history pagination");
        return new TransactionPage(rows,total);
    }
    public JsonNode settlement(String id,int page) {
        if(id==null || !id.matches("[0-9]{1,20}") || page<1 || page>1000)
            throw new BadRequestException("Invalid settlement lookup");
        return data(get("/settlements/{id}?page={page}",id,page));
    }
    public JsonNode resolveBank(String accountNumber,String bankCode) {
        if (accountNumber==null || !accountNumber.matches("[0-9]{10}") || bankCode==null || !bankCode.matches("[0-9]{2,6}"))
            throw new BadRequestException("Enter a valid Nigerian bank and account number");
        return data(post("/accounts/resolve",Map.of("account_number",accountNumber,"account_bank",bankCode)));
    }
    private void code(String value) {
        if (value==null || !value.matches("[A-Za-z0-9_-]{1,40}")) throw new BadRequestException("Invalid bill identifier");
    }
    public ProviderResult submit(MoneyOrder order,String email) {
        BigDecimal amount=BigDecimal.valueOf(order.providerAmount(),2);
        if (order.getKind()==MoneyOrder.Kind.DEPOSIT) {
            if(settings.inlineConfigured()) return ProviderResult.pending(order.getReference());
            JsonNode d=data(post("/payments",Map.of("tx_ref",order.getReference(),"amount",amount,"currency","NGN",
                "redirect_url",settings.getPublicUrl()+"/?payment=returned","customer",Map.of("email",email))));
            String link=d.path("link").asText();
            java.net.URI uri=java.net.URI.create(link);
            if (!"https".equals(uri.getScheme()) || !"checkout.flutterwave.com".equals(uri.getHost()))
                throw new IllegalStateException("Untrusted checkout URL");
            return new ProviderResult(ProviderResult.State.PENDING,order.getReference(),0,"NGN",null,null,link,0);
        }
        if (order.getKind()==MoneyOrder.Kind.WITHDRAWAL) {
            if (!settings.isTransfersApproved()) throw new IllegalStateException("Transfers are not activated");
            Map<String,Object> request=new HashMap<>(Map.of("account_bank",order.getServiceId(),"account_number",order.getDestination(),
                "amount",amount,"currency","NGN","debit_currency","NGN","reference",order.getReference(),
                "narration","SavEx withdrawal"));
            if(order.getDebitSubaccount()!=null) {payoutReference(order.getDebitSubaccount());request.put("debit_subaccount",order.getDebitSubaccount());}
            JsonNode d=data(post("/transfers",request));
            // Submission acknowledgement never settles a wallet. Re-query the transfer.
            return new ProviderResult(ProviderResult.State.PENDING,order.getReference(),0,"NGN",null,d.path("id").asText(),null,0);
        }
        settings.requireBills(); code(order.getServiceId()); code(order.getVariationCode());
        JsonNode d=data(post("/billers/"+order.getServiceId()+"/items/"+order.getVariationCode()+"/payment",
            Map.of("country","NG","customer_id",order.getDestination(),"reference",order.getReference(),"amount",amount)));
        return new ProviderResult(ProviderResult.State.PENDING,order.getReference(),0,"NGN",null,d.path("tx_ref").asText(),null,0,token(d));
    }
    public ProviderResult verify(MoneyOrder order) {
        JsonNode d;
        String ref, destination=null;
        if (order.getKind()==MoneyOrder.Kind.DEPOSIT) {
            // Once an attempt is saved, never accidentally reconcile another attempt sharing tx_ref.
            String attempt=order.getProviderReference();
            if(attempt!=null && !attempt.isBlank()) {
                if(!attempt.matches("[0-9]{1,20}")) throw new IllegalStateException("Invalid saved payment transaction ID");
                d=data(get("/transactions/{id}/verify",attempt));
                if(!attempt.equals(d.path("id").asText())) return review(order);
            } else d=data(get("/transactions/verify_by_reference?tx_ref={ref}",order.getReference()));
            ref=d.path("tx_ref").asText();
        } else if (order.getKind()==MoneyOrder.Kind.WITHDRAWAL) {
            String saved=order.getProviderReference();
            if(saved!=null && !saved.isBlank()) {
                if(!saved.matches("[0-9]{1,20}")) throw new IllegalStateException("Invalid saved transfer ID");
                d=data(get("/transfers/{id}",saved));
                if(!saved.equals(d.path("id").asText()))return review(order);
            } else {
                JsonNode list=data(get("/transfers?reference={ref}",order.getReference()));
                if(!list.isArray())throw new IllegalStateException("Invalid transfer lookup");
                d=null;
                for (JsonNode row:list) if(order.getReference().equals(row.path("reference").asText())) {
                    if(d!=null) throw new IllegalStateException("Multiple provider transfers for one reference");
                    d=row;
                }
            }
            if(d==null) return ProviderResult.pending(order.getReference());
            if(order.getDebitSubaccount()!=null && d.hasNonNull("debit_subaccount") && !order.getDebitSubaccount().equals(d.path("debit_subaccount").asText()))return review(order);
            if(order.getDebitSubaccount()!=null && (saved==null || saved.isBlank()) && !order.getDebitSubaccount().equals(d.path("debit_subaccount").asText()))return review(order);
            ref=d.path("reference").asText(); destination=d.path("account_number").asText();
            if(!order.getServiceId().equals(d.path("bank_code").asText()) || !order.getReference().equals(ref)
                || !order.getDestination().equals(destination) || !"NGN".equals(d.path("currency").asText())
                || order.providerAmount()!=kobo(d.path("amount")) || !d.path("id").asText().matches("[0-9]{1,20}")) return review(order);
        } else {
            String lookup=order.getProviderReference();
            if(lookup==null || lookup.isBlank()) lookup=order.getReference();
            d=data(get("/bills/{ref}?verbose=1",lookup));
            ref=d.path("customer_reference").asText();
            destination=d.path("customer_id").asText();
            if(destination.matches("\\+234[789][01][0-9]{8}")) destination="0"+destination.substring(4);
            if(order.getProviderReference()!=null && !order.getProviderReference().isBlank()
                && !order.getProviderReference().equals(d.path("tx_ref").asText()))return review(order);
        }
        String status=d.path("status").asText().toLowerCase(Locale.ROOT);
        // Some verbose bill responses omit a terminal status. HTTP success alone must never debit a wallet.
        if(BillService.category(order.getKind())!=null && status.isBlank())return ProviderResult.pending(order.getReference());
        ProviderResult.State state=switch(status) {
            case "successful","success" -> ProviderResult.State.SUCCESS;
            case "failed" -> ProviderResult.State.FAILED;
            case "reversed" -> ProviderResult.State.REVERSED;
            case "pending","new","processing" -> ProviderResult.State.PENDING;
            default -> ProviderResult.State.REVIEW;
        };
        if(state==ProviderResult.State.PENDING) return ProviderResult.pending(order.getReference());
        // A successful HTTP response without a documented terminal payment status is not proof of delivery.
        if(state==ProviderResult.State.REVIEW) return review(order);
        long providerFee=order.getKind()!=MoneyOrder.Kind.DEPOSIT?kobo(d.path("fee")):
            d.hasNonNull("app_fee")?kobo(d.path("app_fee")):0;
        if(providerFee<0)return review(order);
        return new ProviderResult(state,ref,kobo(d.path("amount")),d.path("currency").asText(),
            destination,BillService.category(order.getKind())!=null?d.path("tx_ref").asText(null):d.path("id").asText(null),null,providerFee,token(d));
    }
    private String token(JsonNode data) {
        JsonNode value=data.path("recharge_token");
        if(value.isMissingNode() || value.isNull())value=data.path("extra");
        return value.isTextual() && !value.asText().isBlank() && value.asText().length()<=256 ? value.asText() : null;
    }
    private ProviderResult review(MoneyOrder o) {
        return new ProviderResult(ProviderResult.State.REVIEW,o.getReference(),0,"NGN",null,null,null,0);
    }
}
