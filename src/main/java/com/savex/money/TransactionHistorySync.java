package com.savex.money;
import com.fasterxml.jackson.databind.JsonNode;
import com.savex.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.savex.money.TransactionComparison.*;

/** Bounded, read-only imports on a dedicated thread so payment workers are never blocked by history paging. */
@Service @RequiredArgsConstructor
public class TransactionHistorySync {
    private final MoneySettings settings;
    private final FlutterwaveGateway gateway;
    private final MoneyOrderRepository orders;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(work->{Thread thread=new Thread(work,"savex-history-sync");thread.setDaemon(true);return thread;});
    private final AtomicBoolean running=new AtomicBoolean();
    private volatile Snapshot latest;
    private volatile Instant lastAttempt=Instant.EPOCH;
    public record Snapshot(String mode,String state,LocalDate from,LocalDate to,Instant checkedAt,Instant attemptedAt,
        int pagesRead,boolean complete,String message,List<Row> rows) {}
    public record Report(String mode,String state,LocalDate from,LocalDate to,Instant checkedAt,Instant attemptedAt,
        int pagesRead,boolean complete,String message,List<Row> content,int page,boolean last,int totalRows,Map<String,Long> counts) {}
    private Snapshot empty(String state,String message) {
        return new Snapshot(settings.getMode().name(),state,null,null,null,null,0,false,message,List.of());
    }
    public Snapshot snapshot() {
        if(settings.getMode()==MoneySettings.Mode.OFF)return empty("OFF","Payments are disabled; no provider history is queried.");
        Snapshot result=latest;
        return result==null || !settings.getMode().name().equals(result.mode())?empty("NOT_SYNCED","Transaction history has not been synced in this environment yet."):result;
    }
    @Scheduled(fixedDelayString="${savex.money.history-sync-delay-ms:300000}",initialDelayString="${savex.money.history-sync-initial-delay-ms:15000}")
    public void scheduled() {
        if(settings.isHistorySyncEnabled() && settings.isWorkerEnabled() && settings.getMode()!=MoneySettings.Mode.OFF)request();
    }
    public Snapshot request() {
        settings.requireEnabled();
        if(lastAttempt.plusSeconds(60).isAfter(Instant.now()) || !running.compareAndSet(false,true))return snapshot();
        start();
        executor.execute(this::execute);
        return snapshot();
    }
    // Synchronous entry for isolated fixture tests; production HTTP always dispatches asynchronously.
    void syncNow() {if(running.compareAndSet(false,true)){start();execute();}}
    private void start() {
        lastAttempt=Instant.now();LocalDate today=LocalDate.now(ZoneId.of("Africa/Lagos"));
        latest=new Snapshot(settings.getMode().name(),"RUNNING",today.minusDays(settings.getHistoryDays()-1L),today,
            null,lastAttempt,0,false,"Import in progress. Results are report-only and do not change balances or account restrictions.",List.of());
    }
    private Attempt normalize(JsonNode row) {
        String id=row.path("id").asText(),ref=row.path("tx_ref").asText();
        if(!id.matches("[0-9]{1,20}") || ref.length()>80)throw new IllegalStateException("Invalid history row identity");
        Long amount=null;try{amount=FlutterwaveGateway.kobo(row.path("amount"));}catch(RuntimeException ignored){}
        String status=row.path("status").asText().toLowerCase(Locale.ROOT);
        if("success".equals(status))status="successful";
        if(status.length()>24)status="unknown";
        String currency=row.path("currency").asText();if(currency.length()>3)currency="";
        String created=row.path("created_at").asText();if(created.length()>64)created="";
        return new Attempt(id,ref,status,amount,currency,created,false);
    }
    private void execute() {
        Snapshot started=latest;Map<String,Attempt> attempts=new LinkedHashMap<>();int pages=0;
        boolean complete=true;String message="Imported provider collection history. Comparisons are not settlement or bank-receipt confirmation.";
        List<String> unavailable=new ArrayList<>();
            // Explicit successful/failed queries prevent a provider's default successful-only filter hiding failures.
            for(String filter:Arrays.asList(null,"successful","failed")) {
                try {
                boolean finished=false;
                for(int page=1;page<=settings.getHistoryMaxPages();page++) {
                    var response=gateway.transactionHistory(started.from(),started.to(),page,filter);pages++;
                    if(response.rows().isEmpty() && response.totalPages()>page)throw new IllegalStateException("Provider omitted an expected page");
                    for(JsonNode raw:response.rows()) {
                        // This merchant can process unrelated business payments. Never import their customer details.
                        if(!raw.path("tx_ref").asText().startsWith("savex-"))continue;
                        Attempt next=normalize(raw),previous=attempts.get(next.id());
                        if(previous!=null && (!previous.reference().equals(next.reference()) || !previous.status().equals(next.status())
                            || !Objects.equals(previous.amountKobo(),next.amountKobo()) || !previous.currency().equals(next.currency())))
                            next=new Attempt(next.id(),next.reference(),next.status(),next.amountKobo(),next.currency(),next.createdAt(),true);
                        else if(previous!=null && previous.inconsistent())next=previous;
                        if(!attempts.containsKey(next.id()) && attempts.size()>=settings.getHistoryMaxRows())throw new IllegalStateException("History row limit reached");
                        attempts.put(next.id(),next);
                    }
                    if(response.rows().isEmpty() || (response.totalPages()>=0 && page>=response.totalPages())){finished=true;break;}
                }
                if(!finished)throw new IllegalStateException("History page limit reached");
                }catch(RuntimeException exception){
                    complete=false;
                    String reason=exception instanceof org.springframework.web.reactive.function.client.WebClientResponseException response?
                        "HTTP "+response.getStatusCode().value():"limit reached or response unavailable";
                    unavailable.add((filter==null?"unfiltered":filter)+": "+reason);
                }
            }
        if(!complete)message="Provider import incomplete ("+String.join("; ",unavailable)+"). Missing records are not proof of failure. Check provider access or sync limits and retry. No financial changes were made.";
        try {
            int cap=settings.getHistoryMaxRows();
            var local=orders.historyWindow(started.mode(),MoneyOrder.Kind.DEPOSIT,started.from().atStartOfDay(ZoneOffset.UTC).toInstant(),
                started.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),PageRequest.of(0,cap+1));
            if(local.size()>cap){complete=false;message="Local order limit reached; this report is incomplete. Increase the configured limit or shorten the date window.";local=local.subList(0,cap);}
            Map<String,MoneyOrder> byReference=new LinkedHashMap<>();local.stream().filter(order->order.getDebitSubaccount()==null).forEach(order->byReference.put(order.getReference(),order));
            List<String> refs=attempts.values().stream().map(Attempt::reference).distinct().toList();
            for(int offset=0;offset<refs.size();offset+=250)
                orders.findByReferenceIn(refs.subList(offset,Math.min(refs.size(),offset+250))).forEach(order->byReference.put(order.getReference(),order));
            Map<String,Long> successes=new HashMap<>();attempts.values().stream().filter(a->"successful".equals(a.status()))
                .forEach(a->successes.merge(a.reference(),1L,Long::sum));
            List<Row> rows=new ArrayList<>();
            for(Attempt attempt:attempts.values())rows.add(compare(byReference.get(attempt.reference()),attempt,started.mode(),successes.getOrDefault(attempt.reference(),0L)));
            Set<String> seenReferences=new HashSet<>();attempts.values().forEach(a->seenReferences.add(a.reference()));
            Set<String> seenAttempts=new HashSet<>();attempts.values().forEach(a->seenAttempts.add(a.reference()+":"+a.id()));
            for(MoneyOrder order:byReference.values()) {
                String saved=order.getProviderReference();boolean savedMissing=saved!=null && !saved.isBlank()
                    && !seenAttempts.contains(order.getReference()+":"+saved);
                if(!seenReferences.contains(order.getReference()) || savedMissing) {
                    String result=complete?(savedMissing?"SAVED_ATTEMPT_NOT_FOUND":"NOT_FOUND_IN_IMPORT"):"NOT_FOUND_IN_PARTIAL_IMPORT";
                    rows.add(row(order,new Attempt(saved,order.getReference(),"NOT_FOUND",null,order.getCurrency(),"",false),result,"WARNING",
                        "Not found in the imported date range/status pages. This is not proof of failure; verify the specific transaction and date range."));
                }
            }
            rows.sort(Comparator.comparingInt((Row row)->switch(row.severity()){case "CRITICAL"->0;case "WARNING"->1;case "INFO"->2;default->3;})
                .thenComparing(Row::reference).thenComparing(row->Objects.toString(row.providerId(),"")));
            latest=new Snapshot(started.mode(),complete?"COMPLETE":pages==0?"FAILED":"PARTIAL",started.from(),started.to(),Instant.now(),
                started.attemptedAt(),pages,complete,message,List.copyOf(rows));
        } catch(RuntimeException exception) {
            latest=new Snapshot(started.mode(),"FAILED",started.from(),started.to(),null,started.attemptedAt(),pages,false,
                "Comparison could not be completed. Retry when the provider and database are available. No balances or restrictions were changed.",List.of());
        } finally {running.set(false);}
    }
    public Report report(int page,String filter) {
        if(page<0 || page>100_000 || !Set.of("ALL","ATTENTION","MATCHED","SUCCESS","FAILED").contains(filter))throw new BadRequestException("Invalid report filter or page");
        var snapshot=snapshot();
        List<Row> filtered=snapshot.rows().stream().filter(row->switch(filter){
            case "ATTENTION"->Set.of("CRITICAL","WARNING").contains(row.severity());case "MATCHED"->"MATCHED".equals(row.severity());
            case "SUCCESS"->"successful".equals(row.providerStatus());case "FAILED"->"failed".equals(row.providerStatus());default->true;}).toList();
        int offset=Math.min(filtered.size(),page*50),end=Math.min(filtered.size(),offset+50);
        Map<String,Long> counts=new TreeMap<>();snapshot.rows().forEach(row->counts.merge(row.result(),1L,Long::sum));
        return new Report(snapshot.mode(),snapshot.state(),snapshot.from(),snapshot.to(),snapshot.checkedAt(),snapshot.attemptedAt(),snapshot.pagesRead(),
            snapshot.complete(),snapshot.message(),filtered.subList(offset,end),page,end>=filtered.size(),filtered.size(),counts);
    }
    @PreDestroy void close(){executor.shutdownNow();}
}
