package com.savex.money;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ApplicationFeeTest {
    @Test void configurableRateMinimumAndMaximumAreValidated() {
        assertThat(ApplicationFee.calculate(2_000_000,50,0,50_000)).isEqualTo(10_000);
        assertThat(ApplicationFee.calculate(100,100,200,1000)).isEqualTo(200);
        assertThat(ApplicationFee.calculate(2_000_000,100,200,1000)).isEqualTo(1000);
        assertThat(ApplicationFee.calculate(0,100,200,1000)).isZero();
        assertThatThrownBy(()->ApplicationFee.calculate(100,10001,0,100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ApplicationFee.calculate(100,50,200,100)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void feesUseIntegerKoboHalfUpAndNeverExceedFiveHundredNaira() {
        assertThat(ApplicationFee.calculate(1_000_000)).isEqualTo(5_000);
        assertThat(ApplicationFee.calculate(10_000_000)).isEqualTo(50_000);
        assertThat(ApplicationFee.calculate(100_000_000)).isEqualTo(50_000);
        assertThat(ApplicationFee.calculate(100)).isEqualTo(1);
        assertThat(ApplicationFee.calculate(299)).isEqualTo(1);
        assertThat(ApplicationFee.calculate(300)).isEqualTo(2);
        assertThat(ApplicationFee.calculate(Long.MAX_VALUE)).isEqualTo(50_000);
        assertThatThrownBy(()->ApplicationFee.calculate(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
