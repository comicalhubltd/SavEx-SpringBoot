package com.savex.money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;
@Service @RequiredArgsConstructor
public class PayoutWalletReport {
 private final PayoutWalletRepository wallets;
 private final PayoutFundingEventRepository events;
 private final JournalRepository journal;
 public record Row(UUID customerId,String mode,String state,String maskedAccount,long balanceKobo,long reservedKobo,long journalAvailableKobo,long journalReservedKobo,boolean matched,Long providerAvailableKobo,Instant checkedAt,String note) {}
 public record FundingRow(String transferId,String mode,String state,Instant createdAt) {}
 public record Report(List<Row> content,int page,boolean last,long total,List<FundingRow> fundingEvents) {}
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public Report report(int page) {
    if(page<0 || page>100000)throw new com.savex.exception.BadRequestException("Invalid report page");
    var result=wallets.findAll(PageRequest.of(page,50,Sort.by("id")));Set<String> accounts=new HashSet<>();
    result.forEach(w->{accounts.add(w.availableAccount());accounts.add(w.heldAccount());});Map<String,Long> net=new HashMap<>();
    if(!accounts.isEmpty()) {journal.credits(accounts).forEach(t->net.merge(t.getAccount(),t.getAmount(),Math::addExact));journal.debits(accounts).forEach(t->net.merge(t.getAccount(),-t.getAmount(),Math::addExact));}
    var rows=result.stream().map(w->{long available=net.getOrDefault(w.availableAccount(),0L),held=net.getOrDefault(w.heldAccount(),0L);
        return new Row(w.getUserId(),w.getProviderMode(),w.getState(),w.getAccountNumber()==null?null:"******"+w.getAccountNumber().substring(6),w.getBalanceKobo(),w.getLockedKobo(),available,held,
            available>=0 && held>=0 && w.available()==available && w.getLockedKobo()==held,w.getProviderAvailableKobo(),w.getCheckedAt(),w.getSyncNote());}).toList();
    var funding=events.findAll(PageRequest.of(0,100,Sort.by(Sort.Direction.DESC,"createdAt"))).map(e->new FundingRow(e.getTransferId(),e.getProviderMode(),e.getState(),e.getCreatedAt())).getContent();
    return new Report(rows,page,result.isLast(),result.getTotalElements(),funding);
 }
}
