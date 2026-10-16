package com.savex.service;
import com.savex.entity.Wallet;
import com.savex.repository.WalletRepository;
import com.savex.exception.BadRequestException;
import com.savex.exception.NotFoundException;
import com.savex.money.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class PaymentService {
    private final WalletRepository walletRepository;
    private final MoneyLedger ledger;
    private final MoneySettings settings;
    public MoneyView initiateWalletFunding(UUID userId,long amountNaira,String key) {
        settings.requireEnabled();
        if(settings.isFeesEnabled()) throw new BadRequestException("Use the payment screen to review and accept the SavEx fee");
        if(amountNaira < 1 || amountNaira > settings.getMaxTransactionKobo()/100)
            throw new BadRequestException("Invalid NGN payment amount");
        return ledger.create(userId,key,new MoneyDraft(MoneyOrder.Kind.DEPOSIT,
            Math.multiplyExact(amountNaira,100),"NGN",null,null,null,null,null,null,null));
    }
    public void handlePaystackWebhook(String event,String reference) {
        throw new BadRequestException("Legacy payment callbacks are disabled");
    }
    @Transactional(readOnly=true)
    public Wallet getWallet(UUID userId) {
        return walletRepository.findByUserId(userId).orElseThrow(() -> new NotFoundException("Wallet not found"));
    }
}
