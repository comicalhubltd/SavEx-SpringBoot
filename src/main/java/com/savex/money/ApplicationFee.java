package com.savex.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** NGN application fee. Integer kobo, rounded half up, independently of provider fees. */
public final class ApplicationFee {
    private ApplicationFee() {}
    public static long calculate(long amountKobo) {
        return calculate(amountKobo,50,0,50_000);
    }
    public static long calculate(long amountKobo,int basisPoints,long minimumKobo,long maximumKobo) {
        if (amountKobo < 0) throw new IllegalArgumentException("Negative payment amount");
        if(basisPoints<0 || basisPoints>10_000 || minimumKobo<0 || maximumKobo<minimumKobo)
            throw new IllegalArgumentException("Invalid service fee settings");
        if(amountKobo==0) return 0;
        long proportional=BigDecimal.valueOf(amountKobo).multiply(BigDecimal.valueOf(basisPoints))
            .movePointLeft(4).setScale(0,RoundingMode.HALF_UP).longValueExact();
        return Math.min(maximumKobo,Math.max(minimumKobo,proportional));
    }
}
