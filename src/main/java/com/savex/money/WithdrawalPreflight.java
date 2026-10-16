package com.savex.money;

import com.savex.exception.BadRequestException;
import com.savex.verification.AccountAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.UUID;

/** Read-only provider checks outside the wallet transaction; no transfer is initiated here. */
@Service @RequiredArgsConstructor
public class WithdrawalPreflight {
    private final MoneySettings settings;
    private final AccountAssessmentService assessments;
    private final FlutterwaveGateway gateway;

    public void check(UUID customer,MoneyDraft draft) {
        checkRecipient(customer,draft);
        try {
            long fee=gateway.transferFee(draft.amountKobo());
            var balances=gateway.walletBalances();
            if(!balances.isArray()) throw new IllegalStateException("Invalid provider balance");
            Long available=null;
            for(var row:balances) if("NGN".equals(row.path("currency").asText())) {
                if(available!=null) throw new IllegalStateException("Duplicate provider balance");
                available=FlutterwaveGateway.kobo(row.path("available_balance"));
            }
            if(available==null) throw new IllegalStateException("Missing provider balance");
            if(available<Math.addExact(draft.amountKobo(),fee))
                throw new BadRequestException("Withdrawal funding is temporarily insufficient, including the provider transfer fee. No withdrawal was created.");
        }catch(BadRequestException error) {throw error;}
        catch(RuntimeException error) {throw unavailable(error);}
    }
    public void checkRecipient(UUID customer,MoneyDraft draft) {
        settings.requireEnabled();
        if(!settings.isTransfersApproved()) throw new BadRequestException("Bank transfers are awaiting provider activation");
        var assessment=assessments.assess(customer);
        if(!"ACTIVE".equals(assessment.accountStatus()) ||
            (settings.getMode()==MoneySettings.Mode.LIVE && !assessment.fundingAvailable()))
            throw new BadRequestException("Withdrawal requires an active approved account in the live pilot allowlist");
        if(!"NGN".equals(draft.currency()) || draft.amountKobo()<100 || draft.amountKobo()>settings.getMaxTransactionKobo()
            || (settings.getMode()==MoneySettings.Mode.LIVE && draft.amountKobo()>settings.getLiveMaxTransactionKobo()))
            throw new BadRequestException("Invalid withdrawal amount or pilot limit exceeded");
        try {
            var resolved=gateway.resolveBank(draft.destination(),draft.serviceId());
            if(!draft.destination().equals(resolved.path("account_number").asText()) || resolved.path("account_name").asText().isBlank())
                throw new BadRequestException("The recipient bank account could not be confirmed");
        } catch(BadRequestException exception) {throw exception;}
        catch(RuntimeException exception) {
            throw unavailable(exception);
        }
    }
    private BadRequestException unavailable(RuntimeException error) {
        if(error instanceof WebClientResponseException response && response.getStatusCode().value()==403)
            return new BadRequestException("Flutterwave denied withdrawal checks (403). Ask the administrator to confirm Transfers access and the server IP allowlist. No withdrawal was created.");
        return new BadRequestException("Recipient, transfer fee or provider funds could not be confirmed. Try again shortly. No withdrawal was created.");
    }
}
