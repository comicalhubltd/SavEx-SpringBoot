package com.savex.money;
import com.fasterxml.jackson.databind.JsonNode;
import com.savex.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class BillService {
    private final MoneyGateway gateway;
    public static String category(MoneyOrder.Kind kind) {
        return switch(kind) {
            case AIRTIME -> "AIRTIME";
            case DATA -> "MOBILEDATA";
            case ELECTRICITY -> "UTILITYBILLS";
            case CABLE -> "CABLEBILLS";
            case INTERNET -> "INTSERVICE";
            default -> null;
        };
    }
    public static boolean customerCheckRequired(MoneyOrder.Kind kind) {
        return kind==MoneyOrder.Kind.ELECTRICITY || kind==MoneyOrder.Kind.CABLE || kind==MoneyOrder.Kind.INTERNET;
    }
    public record Quote(String customerName,String customer,long amountKobo,long providerFeeKobo) {}
    public Quote validate(MoneyDraft draft) {
        String category=category(draft.kind());
        if(category==null) throw new BadRequestException("Select a supported bill service");
        if(draft.destination()==null || !draft.destination().matches("[A-Za-z0-9@._+\\-]{4,64}"))
            throw new BadRequestException("Enter a valid customer account or meter number");
        if((draft.kind()==MoneyOrder.Kind.AIRTIME || draft.kind()==MoneyOrder.Kind.DATA) && !draft.destination().matches("0[789][01][0-9]{8}"))
            throw new BadRequestException("Enter an 11-digit Nigerian mobile number starting with 0");
        boolean found=false;
        for(JsonNode biller:gateway.billers(category))
            if(draft.serviceId()!=null && draft.serviceId().equals(biller.path("biller_code").asText())) found=true;
        if(!found) throw new BadRequestException("Select an available provider for this bill type");
        JsonNode selected=null;
        for(JsonNode item:gateway.items(draft.serviceId()))
            if(draft.variationCode()!=null && draft.variationCode().equals(item.path("item_code").asText())) selected=item;
        if(selected==null) throw new BadRequestException("This package is not available");
        long price=FlutterwaveGateway.kobo(selected.path("amount"));
        long itemFee=selected.hasNonNull("fee")?FlutterwaveGateway.kobo(selected.path("fee")):0;
        if(itemFee<0)throw new BadRequestException("The provider returned an invalid fee");
        if(price<0 || ((draft.kind()==MoneyOrder.Kind.DATA || price>0) && price!=draft.amountKobo()))
            throw new BadRequestException("The package price has changed. Select the package again.");
        if(!customerCheckRequired(draft.kind())) return new Quote("",draft.destination(),draft.amountKobo(),itemFee);
        JsonNode customer=gateway.validateBill(draft.variationCode(),draft.destination());
        if(!"00".equals(customer.path("response_code").asText()) ||
           !draft.destination().equals(customer.path("customer").asText()) ||
           !draft.serviceId().equals(customer.path("biller_code").asText()) ||
           !draft.variationCode().equals(customer.path("product_code").asText()) ||
           customer.path("name").asText().isBlank())
            throw new BadRequestException("The provider could not validate this customer. Check the meter or account number.");
        long minimum=FlutterwaveGateway.kobo(customer.path("minimum"));
        long maximum=FlutterwaveGateway.kobo(customer.path("maximum"));
        long fee=FlutterwaveGateway.kobo(customer.path("fee"));
        if(minimum<0 || maximum<0 || fee<0) throw new BadRequestException("The provider returned invalid billing limits");
        if(draft.amountKobo()<minimum || (maximum>0 && draft.amountKobo()>maximum))
            throw new BadRequestException("The amount is outside this provider's permitted range");
        // Provider charges are borne by SavEx, never silently added to the customer debit.
        return new Quote(customer.path("name").asText(),draft.destination(),draft.amountKobo(),Math.max(itemFee,fee));
    }
}
