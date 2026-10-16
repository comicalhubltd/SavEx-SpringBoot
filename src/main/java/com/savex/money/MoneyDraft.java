package com.savex.money;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record MoneyDraft(
 @NotNull MoneyOrder.Kind kind,
 @Min(100) @Max(100000000) long amountKobo,
 @NotBlank @Pattern(regexp="NGN",message="Only approved NGN operations are available") String currency,
 @Size(max=120) String destination,
 @Size(max=40) String serviceId,
 @Size(max=120) String variationCode,
 UUID groupId, @Min(1) @Max(50) Integer cycleNumber,
 @Size(max=72) String password, @Pattern(regexp="\\d{6}") String authorizationCode,
 @PositiveOrZero Long applicationFeeKobo
) {
 public MoneyDraft(MoneyOrder.Kind kind,long amount,String currency,String destination,String service,
    String variation,UUID group,Integer cycle,String password,String code) {
    this(kind,amount,currency,destination,service,variation,group,cycle,password,code,null);
 }
}
