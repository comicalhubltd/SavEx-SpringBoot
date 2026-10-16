package com.savex.money;
import com.fasterxml.jackson.databind.JsonNode;
public interface MoneyGateway {
    ProviderResult submit(MoneyOrder order, String email);
    ProviderResult verify(MoneyOrder order);
    JsonNode billers(String category);
    JsonNode items(String biller);
    JsonNode validateBill(String itemCode,String customer);
    JsonNode banks();
    JsonNode resolveBank(String accountNumber, String bankCode);
}
