package com.savex.money;
import com.fasterxml.jackson.databind.*;
import com.savex.exception.*;
import com.savex.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service @RequiredArgsConstructor
public class AiEvidence {
    private final MoneyOrderRepository orders;
    private final MoneySettings settings;
    private final SettlementMatchRepository settlements;
    private final BankSettlementReceiptRepository receipts;
    private final TransactionHistorySync sync;
    private final WalletRepository wallets;
    private final JournalRepository journal;
    private final ObjectMapper json;
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public JsonNode collect(String reference) {
        var order=orders.findByReference(reference).orElseThrow(()->new NotFoundException("Transaction not found"));
        if(!settings.getMode().name().equals(order.getProviderMode()))throw new BadRequestException("Transaction belongs to another payment environment");
        Map<String,Object> evidence=new LinkedHashMap<>();
        evidence.put("checkedAt",Instant.now().toString());evidence.put("currency",order.getCurrency());
        evidence.put("service",order.getKind());evidence.put("localStatus",order.getStatus());
        evidence.put("principalKobo",order.getAmountKobo());evidence.put("applicationFeeKobo",order.getApplicationFeeKobo());
        evidence.put("providerIdSaved",order.getProviderReference()!=null);evidence.put("createdAt",order.getCreatedAt().toString());
        evidence.put("bankReceiptRecorded",receipts.existsByOrderId(order.getId()));
        evidence.put("collectionSettlementEvidenceApplies",order.getKind()==MoneyOrder.Kind.DEPOSIT);
        var settlement=settlements.findByOrderId(order.getId());
        evidence.put("providerSettlementRecorded",settlement.isPresent());
        settlement.ifPresent(s->evidence.put("settlementAmounts",Map.of("grossKobo",s.getGrossKobo(),"providerFeeKobo",s.getProviderFeeKobo(),"matchedAt",s.getMatchedAt().toString())));
        wallets.findByUserId(order.getUserId()).ifPresent(w->{
            String root="wallet:"+order.getUserId();var accounts=Set.of(root+":available",root+":held");
            Map<String,Long> amounts=new HashMap<>();journal.credits(accounts).forEach(p->amounts.merge(p.getAccount(),p.getAmount(),Math::addExact));
            journal.debits(accounts).forEach(p->amounts.merge(p.getAccount(),-p.getAmount(),Math::addExact));
            long available=amounts.getOrDefault(root+":available",0L),held=amounts.getOrDefault(root+":held",0L);
            evidence.put("walletJournal",Map.of("walletBalanceKobo",w.getBalanceKobo(),"reservedKobo",w.getLockedKobo(),"journalAvailableKobo",available,"journalHeldKobo",held,
                "matched",w.getBalanceKobo()==available+held && w.getLockedKobo()==held && available>=0 && held>=0));
        });
        var snapshot=sync.snapshot();
        evidence.put("historyImport",Map.of("state",snapshot.state(),"complete",snapshot.complete(),"checkedAt",Objects.toString(snapshot.checkedAt(),"not checked"),
            "coverage","Cached collection history only; not a live transfer lookup or complete bank statement"));
        evidence.put("matchingHistoryRows",snapshot.rows().stream().filter(r->reference.equals(r.reference())).limit(20)
            .map(r->Map.of("comparison",r.result(),"severity",r.severity(),"providerStatus",r.providerStatus())).toList());
        evidence.put("limitations","No live Flutterwave call, bank statement fetch or restriction lookup was performed by this assistant. Amounts are integer kobo. Customer and provider identifiers omitted.");
        return json.valueToTree(evidence);
    }
}
