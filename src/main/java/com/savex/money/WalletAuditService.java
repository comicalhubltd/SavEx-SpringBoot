package com.savex.money;
import com.savex.repository.WalletRepository;
import com.savex.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;

/** Read-only liability comparison. It never repairs wallets or changes restrictions. */
@Service @RequiredArgsConstructor
public class WalletAuditService {
    private final WalletRepository wallets;
    private final JournalRepository journal;
    public record Row(UUID walletId,UUID customerId,long walletBalanceKobo,long reservedKobo,
        long journalAvailableKobo,long journalReservedKobo,long differenceKobo,boolean matched) {}
    public record Report(List<Row> content,int page,boolean last,long totalWallets,Instant checkedAt,String note) {}
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Report report(int page) {
        if(page<0 || page>100_000) throw new BadRequestException("Invalid wallet report page");
        var result=wallets.findAll(PageRequest.of(page,50,Sort.by("id")));
        Set<String> accounts=new HashSet<>();
        result.forEach(w->{String root="wallet:"+w.getUser().getId();accounts.add(root+":available");accounts.add(root+":held");});
        Map<String,Long> net=new HashMap<>();
        if(!accounts.isEmpty()) {
            journal.credits(accounts).forEach(t->net.merge(t.getAccount(),t.getAmount(),Math::addExact));
            journal.debits(accounts).forEach(t->net.merge(t.getAccount(),-t.getAmount(),Math::addExact));
        }
        List<Row> rows=result.stream().map(w->{
            UUID user=w.getUser().getId();String root="wallet:"+user;
            long available=net.getOrDefault(root+":available",0L),held=net.getOrDefault(root+":held",0L);
            long difference=Math.subtractExact(w.getBalanceKobo(),Math.addExact(available,held));
            boolean matched=difference==0 && w.getLockedKobo()==held && w.getBalanceKobo()>=0
                && w.getLockedKobo()>=0 && w.getLockedKobo()<=w.getBalanceKobo() && available>=0 && held>=0;
            return new Row(w.getId(),user,w.getBalanceKobo(),w.getLockedKobo(),available,held,difference,matched);
        }).toList();
        return new Report(rows,page,result.isLast(),result.getTotalElements(),Instant.now(),
            "Wallet-to-journal comparison only. Differences, including unrecorded opening balances or legacy transactions, require investigation. A match does not prove provider settlement or bank receipt.");
    }
}
