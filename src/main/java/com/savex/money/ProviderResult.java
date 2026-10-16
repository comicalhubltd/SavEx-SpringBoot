package com.savex.money;
public record ProviderResult(State state,String reference,long amountKobo,String currency,String destination,
 String providerReference,String checkoutUrl,long feeKobo,String rechargeToken) {
 public ProviderResult(State state,String reference,long amountKobo,String currency,String destination,
   String providerReference,String checkoutUrl,long feeKobo) {
   this(state,reference,amountKobo,currency,destination,providerReference,checkoutUrl,feeKobo,null);
 }
 public enum State { PENDING, SUCCESS, FAILED, REVERSED, REVIEW }
 public static ProviderResult pending(String ref) { return new ProviderResult(State.PENDING,ref,0,"NGN",null,null,null,0); }
}
