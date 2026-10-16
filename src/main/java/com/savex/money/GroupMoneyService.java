package com.savex.money;
import com.savex.entity.*;
import com.savex.entity.enums.*;
import com.savex.repository.UserRepository;
import com.savex.exception.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class GroupMoneyService {
    private final MoneySettings settings;
    private final MoneyOrderRepository orders;
    private final JournalRepository journal;
    private final PasswordEncoder passwords;
    private final UserRepository users;
    @PersistenceContext private EntityManager em;
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MoneyView contribute(UUID userId,String key,MoneyDraft draft) {
        settings.requireEnabled();
        if(settings.getMode()==MoneySettings.Mode.LIVE) throw new BadRequestException("Group payments are disabled during the live collections pilot");
        if(key==null || !key.matches("[A-Za-z0-9_-]{16,80}") || draft.groupId()==null || draft.cycleNumber()==null ||
            draft.kind()!=MoneyOrder.Kind.CONTRIBUTION || !"NGN".equals(draft.currency()))
            throw new BadRequestException("Invalid contribution request");
        User authoriser=users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if(draft.password()==null || !passwords.matches(draft.password(),authoriser.getPasswordHash()))
            throw new BadRequestException("Confirm your password");
        Group group=em.find(Group.class,draft.groupId(),LockModeType.PESSIMISTIC_WRITE);
        if(group==null || group.getDeletedAt()!=null) throw new NotFoundException("Circle not found");
        var members=group.getMembers().stream().filter(GroupMember::isActive)
            .sorted(Comparator.<GroupMember,Integer>comparing(m->m.getUser().getId().equals(group.getCreator().getId())?0:1)
                .thenComparing(GroupMember::getJoinedAt).thenComparing(GroupMember::getId)).toList();
        if(members.stream().noneMatch(m->m.getUser().getId().equals(userId))) throw new NotFoundException("Circle not found");
        var old=orders.findByUserIdAndIdempotencyKey(userId,key);
        if(old.isPresent()) {
            if(!old.get().getFingerprint().equals(MoneyLedger.fingerprint(draft))) throw new ConflictException("This request key belongs to a different payment");
            return MoneyView.from(old.get());
        }
        if(group.getStatus()!=GroupStatus.ACTIVE || members.size()!=group.getMaxMembers() || group.getPayoutOrder()!=PayoutOrder.SEQUENTIAL)
            throw new BadRequestException("Contributions require a full, active circle with sequential payouts");
        int cycle=group.getCurrentCycle()+1;
        if(cycle!=draft.cycleNumber() || cycle>members.size()) throw new BadRequestException("Refresh the circle to see its current cycle");
        LocalDate due=switch(group.getCycleFrequency()) {
            case WEEKLY -> group.getStartDate().plusWeeks(cycle-1);
            case BIWEEKLY -> group.getStartDate().plusWeeks((cycle-1)*2L);
            case MONTHLY -> group.getStartDate().plusMonths(cycle-1);
        };
        if(LocalDate.now(ZoneId.of("Africa/Lagos")).isBefore(due)) throw new BadRequestException("This cycle opens on "+due);
        if(draft.amountKobo()!=group.getContributionKobo() || draft.amountKobo()>settings.getMaxTransactionKobo())
            throw new BadRequestException("The contribution must match the circle amount");
        String business="contribution:"+group.getId()+":"+cycle+":"+userId;
        if(orders.findByBusinessKey(business).isPresent()) throw new ConflictException("You have already contributed to this cycle");
        UUID recipient=members.get(cycle-1).getUser().getId();
        Map<UUID,Wallet> locked=new HashMap<>();
        for(UUID id:new TreeSet<>(List.of(userId,recipient))) {
            Wallet w=em.createQuery("select w from Wallet w where w.user.id=:id",Wallet.class)
                .setParameter("id",id).setLockMode(LockModeType.PESSIMISTIC_WRITE).getSingleResult();locked.put(id,w);
        }
        Wallet payer=locked.get(userId);
        if(!payer.getUser().isActive() || !locked.get(recipient).getUser().isActive()) throw new BadRequestException("A participant requires account review");
        long used=orders.debits(userId,Instant.now().minus(Duration.ofDays(1)),
            List.of(MoneyOrder.Kind.WITHDRAWAL,MoneyOrder.Kind.AIRTIME,MoneyOrder.Kind.DATA,MoneyOrder.Kind.ELECTRICITY,MoneyOrder.Kind.CABLE,MoneyOrder.Kind.INTERNET,MoneyOrder.Kind.CONTRIBUTION),
            List.of(MoneyOrder.Status.FAILED,MoneyOrder.Status.REVERSED));
        if(draft.amountKobo()>settings.getDailyDebitLimitKobo()-used || payer.getAvailableKobo()<draft.amountKobo())
            throw new BadRequestException("Insufficient available funds or daily limit reached");
        String escrow="group:"+group.getId()+":cycle:"+cycle;
        MoneyOrder order=new MoneyOrder();order.setUserId(userId);order.setKind(MoneyOrder.Kind.CONTRIBUTION);
        order.setIdempotencyKey(key);order.setFingerprint(MoneyLedger.fingerprint(draft));order.setBusinessKey(business);
        order.setGroupId(group.getId());order.setCycleNumber(cycle);order.setAmountKobo(draft.amountKobo());
        order.setReference("savex-"+UUID.randomUUID());order.setStatus(MoneyOrder.Status.SUCCESS);orders.saveAndFlush(order);
        payer.setBalanceKobo(payer.getBalanceKobo()-draft.amountKobo());
        journal.save(new JournalPosting(order.getReference()+":contribute","wallet:"+userId+":available",escrow,draft.amountKobo()));
        if(orders.countByGroupIdAndCycleNumberAndKindAndStatus(group.getId(),cycle,MoneyOrder.Kind.CONTRIBUTION,MoneyOrder.Status.SUCCESS)==members.size()) {
            long total=Math.multiplyExact(group.getContributionKobo(),members.size());
            MoneyOrder payout=new MoneyOrder();payout.setUserId(recipient);payout.setKind(MoneyOrder.Kind.PAYOUT);
            payout.setIdempotencyKey("payout-"+UUID.randomUUID());payout.setFingerprint(MoneyLedger.fingerprint(draft));
            payout.setBusinessKey("payout:"+group.getId()+":"+cycle);payout.setGroupId(group.getId());payout.setCycleNumber(cycle);
            payout.setAmountKobo(total);payout.setReference("savex-"+UUID.randomUUID());payout.setStatus(MoneyOrder.Status.SUCCESS);orders.save(payout);
            Wallet receiver=locked.get(recipient);receiver.setBalanceKobo(Math.addExact(receiver.getBalanceKobo(),total));
            journal.save(new JournalPosting(payout.getReference()+":payout",escrow,"wallet:"+recipient+":available",total));
            em.persist(PayoutCycle.builder().group(group).cycleNumber((short)cycle).recipient(receiver.getUser())
                .dueDate(due).totalKobo(total).paidOutAt(Instant.now()).status(CycleStatus.PAID_OUT).build());
            group.setCurrentCycle((short)cycle);
            if(cycle==group.getTotalCycles()) group.setStatus(GroupStatus.COMPLETED);
        }
        return MoneyView.from(order);
    }
}
