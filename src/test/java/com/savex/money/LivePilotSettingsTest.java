package com.savex.money;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class LivePilotSettingsTest {
    MoneySettings configured(String profile) {
        StandardEnvironment environment=new StandardEnvironment();environment.setActiveProfiles(profile);
        environment.getPropertySources().addFirst(new MapPropertySource("fixture",Map.of(
            "spring.datasource.url","jdbc:mysql://127.0.0.1/savex_acceptance","spring.jpa.hibernate.ddl-auto","validate")));
        MoneySettings settings=new MoneySettings(environment);settings.setMode(MoneySettings.Mode.LIVE);
        settings.setFlutterwaveSecret("FLWSECK-fixture-only");settings.setWebhookSecret("fixture-secret-of-at-least-thirty-two-characters");
        settings.setPublicUrl("https://example.test");settings.setLivePilotEnabled(true);
        settings.setLiveAllowedUsers(Set.of(UUID.randomUUID()));return settings;
    }
    @Test void liveRequiresPersistentStorageExplicitUsersAndSmallFundingOrActivatedWithdrawals() {
        var increased=configured("xampp");
        assertThat(increased.getLiveMaxTransactionKobo()).isEqualTo(1_000_000);
        assertThat(increased.getLiveDailyLimitKobo()).isEqualTo(2_000_000);
        increased.setTransfersApproved(true);increased.validate();
        increased.setLiveDailyLimitKobo(2_000_001);
        assertThatThrownBy(increased::validate).hasMessageContaining("NGN 20000");
        configured("xampp").validate();
        assertThatThrownBy(()->configured("dev").validate()).hasMessageContaining("persistent");
        var settings=configured("xampp");settings.setTransfersApproved(true);
        settings.validate();
        settings.setBillsApproved(true);
        settings.validate();
        settings=configured("xampp");settings.setLiveAllowedUsers(Set.of());
        assertThatThrownBy(settings::validate).hasMessageContaining("LIVE mode is locked");
        settings=configured("xampp");settings.setLiveMaxTransactionKobo(1000001);
        assertThatThrownBy(settings::validate).hasMessageContaining("at most NGN 10000");
    }
}
