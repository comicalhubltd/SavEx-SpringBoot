package com.savex.money;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface JournalRepository extends JpaRepository<JournalPosting,UUID> {
    boolean existsByPostingKey(String key);
    interface AccountTotal { String getAccount(); Long getAmount(); }
    @org.springframework.data.jpa.repository.Query("select j.creditAccount as account,sum(j.amountKobo) as amount from JournalPosting j where j.creditAccount in :accounts group by j.creditAccount")
    java.util.List<AccountTotal> credits(@org.springframework.data.repository.query.Param("accounts") java.util.Collection<String> accounts);
    @org.springframework.data.jpa.repository.Query("select j.debitAccount as account,sum(j.amountKobo) as amount from JournalPosting j where j.debitAccount in :accounts group by j.debitAccount")
    java.util.List<AccountTotal> debits(@org.springframework.data.repository.query.Param("accounts") java.util.Collection<String> accounts);
}
