package com.savex.money;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import static org.assertj.core.api.Assertions.*;
class IndependentIdentitySettingsTest {
    @Test void identityCanRunWhilePaymentsAreOffButCannotEnablePayments() {
        var settings=new MoneySettings(new StandardEnvironment());settings.setMode(MoneySettings.Mode.OFF);
        settings.setIdentityEnabled(true);settings.setIdentityMode(MoneySettings.Mode.TEST);settings.setIdentitySecret("FLWSECK_TEST-fixture-X");
        settings.requireIdentity();assertThatThrownBy(settings::requireEnabled).isInstanceOf(com.savex.exception.BadRequestException.class);
        settings.setIdentityMode(MoneySettings.Mode.LIVE);
        assertThatThrownBy(settings::requireIdentity).isInstanceOf(com.savex.exception.BadRequestException.class);
        settings.setIdentityEnabled(false);assertThatThrownBy(settings::requireIdentity).isInstanceOf(com.savex.exception.BadRequestException.class);
    }
}
