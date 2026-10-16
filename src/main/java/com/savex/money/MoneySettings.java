package com.savex.money;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import com.savex.exception.BadRequestException;
@Getter @Setter @RequiredArgsConstructor @Component @ConfigurationProperties(prefix="savex.money")
public class MoneySettings {
    public enum Mode { OFF, TEST, LIVE }
    private final Environment environment;
    private Mode mode = Mode.OFF;
    private String flutterwaveSecret = "";
    private String flutterwavePublicKey = "";
    public boolean inlineConfigured() {
        return mode!=Mode.OFF && (flutterwavePublicKey.startsWith("FLWPUBK-") || flutterwavePublicKey.startsWith("FLWPUBK_TEST-"))
            && flutterwavePublicKey.contains("_TEST")== (mode==Mode.TEST);
    }
    public void requireInline() {
        requireEnabled();
        if(!inlineConfigured()) throw new BadRequestException("Configure FLW_PUBLIC_KEY for the current payment environment to enable payments inside SavEx");
    }
    private String webhookSecret = "";
    private String publicUrl = "http://localhost:8080";
    private String approvalReference = "";
    private boolean transfersApproved;
    private boolean billsApproved;
    private boolean payoutWalletsEnabled;
    public void requirePayoutWallets() {
        requireEnabled();
        if(!payoutWalletsEnabled) throw new BadRequestException("Customer funding accounts are awaiting setup");
    }
    private boolean identityEnabled;
    private Mode identityMode = Mode.LIVE;
    private String identitySecret = "";
    public void requireIdentity() {
        if(!identityEnabled) throw new BadRequestException("Identity verification is awaiting provider configuration");
        if(identityMode==Mode.OFF || identitySecret.isBlank()
            || !(identitySecret.startsWith("FLWSECK-") || identitySecret.startsWith("FLWSECK_TEST-"))
            || identitySecret.contains("_TEST")!=(identityMode==Mode.TEST))
            throw new BadRequestException("Configure a separate identity key matching the identity environment");
    }
    private long maxTransactionKobo = 100_000_000L;
    private long dailyDebitLimitKobo = 200_000_000L;
    private boolean workerEnabled = true;
    private boolean historySyncEnabled = true;
    private int historyDays = 30;
    private int historyMaxPages = 50;
    private int historyMaxRows = 5000;
    private boolean feesEnabled = true;
    private int feeBasisPoints = 50;
    private long feeMinimumKobo = 0;
    private long feeMaximumKobo = 50_000;
    public long applicationFee(long amountKobo) {
        return feesEnabled?ApplicationFee.calculate(amountKobo,feeBasisPoints,feeMinimumKobo,feeMaximumKobo):0;
    }
    private boolean livePilotEnabled;
    private java.util.Set<java.util.UUID> liveAllowedUsers = new java.util.HashSet<>();
    private long liveMaxTransactionKobo = 1_000_000L;
    private long liveDailyLimitKobo = 2_000_000L;
    @PostConstruct void validate() {
        if(historyDays<1 || historyDays>366 || historyMaxPages<1 || historyMaxPages>1000 || historyMaxRows<1 || historyMaxRows>50000)
            throw new IllegalStateException("Invalid transaction history sync limits");
        ApplicationFee.calculate(0,feeBasisPoints,feeMinimumKobo,feeMaximumKobo);
        if (mode != Mode.OFF && (!flutterwaveSecret.startsWith("FLWSECK-") && !flutterwaveSecret.startsWith("FLWSECK_TEST-")))
            throw new IllegalStateException("Configure the Flutterwave v3 secret key externally");
        if (mode != Mode.OFF && (flutterwaveSecret.contains("_TEST") != (mode == Mode.TEST)))
            throw new IllegalStateException("Flutterwave key does not match payment mode");
        if (mode != Mode.OFF && webhookSecret.length() < 32)
            throw new IllegalStateException("Configure a random webhook secret of at least 32 characters");
        if (mode == Mode.LIVE) {
            if(!livePilotEnabled || liveAllowedUsers.isEmpty())
                throw new IllegalStateException("LIVE mode is locked until a restricted pilot and allowed users are configured");
            if(environment.acceptsProfiles(Profiles.of("dev")) || !environment.acceptsProfiles(Profiles.of("xampp","prod")))
                throw new IllegalStateException("LIVE requires persistent xampp or prod storage and excludes dev");
            String database=environment.getProperty("spring.datasource.url","");
            if(!database.startsWith("jdbc:mysql:") || !"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto")))
                throw new IllegalStateException("LIVE requires a persistent database with schema validation");
            if(!publicUrl.startsWith("https://") || java.net.URI.create(publicUrl).getHost()==null
                || java.net.URI.create(publicUrl).getUserInfo()!=null)
                throw new IllegalStateException("LIVE requires a public HTTPS URL");
            if(liveMaxTransactionKobo<100 || liveMaxTransactionKobo>1_000_000
                || liveDailyLimitKobo<liveMaxTransactionKobo || liveDailyLimitKobo>2_000_000 || !feesEnabled)
                throw new IllegalStateException("LIVE pilot permits explicitly activated services at most NGN 10000 per payment and NGN 20000 per day per service");
        }
    }
    public void requireEnabled() {
        if (mode == Mode.OFF) throw new BadRequestException("Payments are not enabled. Provider setup is required.");
    }
    public void requireBills() {
        requireEnabled();
        if (!billsApproved) throw new BadRequestException("Bill payments are awaiting provider activation");
    }
}
