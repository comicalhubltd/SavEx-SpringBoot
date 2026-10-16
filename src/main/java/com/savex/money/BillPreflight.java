package com.savex.money;
import com.savex.exception.BadRequestException;
import com.savex.verification.AccountAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class BillPreflight {
    private final MoneySettings settings;
    private final AccountAssessmentService assessments;
    private final BillService bills;
    private final FlutterwaveGateway gateway;
    public void check(java.util.UUID customer,MoneyDraft draft) {
        settings.requireBills();
        var account=assessments.assess(customer);
        if(!"ACTIVE".equals(account.accountStatus()) || (settings.getMode()==MoneySettings.Mode.LIVE && !account.fundingAvailable()))
            throw new BadRequestException("Bill payment requires an active approved account in the live pilot allowlist");
        if(draft.amountKobo()<100 || draft.amountKobo()>settings.getMaxTransactionKobo()
            || (settings.getMode()==MoneySettings.Mode.LIVE && draft.amountKobo()>settings.getLiveMaxTransactionKobo()))
            throw new BadRequestException("Invalid bill amount or pilot limit exceeded");
        try {
            var quote=bills.validate(draft);var rows=gateway.walletBalances();
            if(!rows.isArray())throw new IllegalStateException("Invalid provider balance");
            Long available=null;
            for(var row:rows)if("NGN".equals(row.path("currency").asText())) {
                if(available!=null)throw new IllegalStateException("Duplicate provider balance");
                available=FlutterwaveGateway.kobo(row.path("available_balance"));
            }
            if(available==null)throw new IllegalStateException("Missing source balance");
            if(available<Math.addExact(draft.amountKobo(),quote.providerFeeKobo()))
                throw new BadRequestException("Bill-payment provider funds are insufficient, including its fee. No bill payment was created.");
        }catch(BadRequestException error){throw error;}
        catch(RuntimeException error){throw new BadRequestException("Bill provider, customer details or funding could not be confirmed. No bill payment was created. Ask support to check API access and the server IP allowlist.");}
    }
}
