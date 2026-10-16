package com.savex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savex.entity.enums.UserStatus;
import com.savex.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="savex.admin.setup-token=fixture-admin-setup-token-of-at-least-thirty-two-characters")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class WebApplicationTest {
    @Test void assistantsRequireAuthenticationValidateInputsAndNeverCreatePaymentsInOffMode() throws Exception {
        mvc.perform(post("/api/v1/assistants/savings").contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalKobo\":10000,\"savedKobo\":0,\"periods\":3,\"frequency\":\"WEEKLY\"}")).andExpect(status().isUnauthorized());
        JsonNode account=register();
        mvc.perform(post("/api/v1/assistants/savings").header("Authorization",bearer(account)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalKobo\":10000,\"savedKobo\":0,\"periods\":3,\"frequency\":\"WEEKLY\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.contributionKobo").value(3334));
        mvc.perform(post("/api/v1/assistants/savings").header("Authorization",bearer(account)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalKobo\":10000,\"savedKobo\":0,\"periods\":0,\"frequency\":\"WEEKLY\"}"))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/assistants/transactions/savex-nonexistent").header("Authorization",bearer(account))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/assistants/payments/prepare").header("Authorization",bearer(account)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"kind\":\"DEPOSIT\",\"amountKobo\":10000}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/money/orders").header("Authorization",bearer(account))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired com.savex.verification.EmailChallengeRepository emailChallenges;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void merchantBalanceIsAdminOnlyAndCustomerGetsOnlyFundingStatus() throws Exception {
        JsonNode account=register();
        mvc.perform(get("/api/v1/admin/ai/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/ai/reports").header("Authorization",bearer(account))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/ai/investigate").header("Authorization",bearer(account)).contentType(MediaType.APPLICATION_JSON).content("{\"reference\":\"savex-test\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/ai/daily-review").header("Authorization",bearer(account))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reconciliation/wallets").header("Authorization",bearer(account))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reconciliation/transactions").header("Authorization",bearer(account))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/reconciliation/transactions/sync").header("Authorization",bearer(account))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/money/orders/"+UUID.randomUUID()+"/receipt")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/payments/transfer-balance").header("Authorization",bearer(account)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/money/transfer-balance")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/money/transfer-balance").header("Authorization",bearer(account)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("OFF"))
            .andExpect(jsonPath("$.data.availableKobo").doesNotExist()).andExpect(jsonPath("$.data.ledgerKobo").doesNotExist());
    }
    @Test void firstAdministratorSetupRequiresSecretVerifiedEmailAndIsOneTime() throws Exception {
        JsonNode account=register();UUID id=UUID.fromString(account.path("user").path("id").asText());
        jdbc.update("INSERT INTO admin_setup_lock (id) VALUES (1)");
        var body=Map.of("setupToken","fixture-admin-setup-token-of-at-least-thirty-two-characters","password","Development123!");
        mvc.perform(post("/api/v1/verification/admin-setup").header("Authorization",bearer(account)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        var user=users.findById(id).orElseThrow();user.setEmailVerified(true);users.saveAndFlush(user);
        var email=new com.savex.verification.EmailChallenge();email.setUserId(id);email.setEmail(user.getEmail());email.setVerifiedAt(java.time.Instant.now());emailChallenges.saveAndFlush(email);
        mvc.perform(post("/api/v1/verification/admin-setup").header("Authorization",bearer(account)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("setupToken","wrong-token","password","Development123!")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/verification/admin-setup").header("Authorization",bearer(account)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body))).andExpect(status().isOk());
        assertThat(users.findById(id).orElseThrow().getRole()).isEqualTo(com.savex.entity.enums.UserRole.ADMIN);
        assertThat(users.findById(id).orElseThrow().getKycLevel()).isEqualTo(com.savex.entity.enums.KycStatus.UNVERIFIED);
        mvc.perform(get("/api/v1/admin/customer-reviews").header("Authorization",bearer(account))).andExpect(status().isOk());
        var reportResponse=mvc.perform(get("/api/v1/admin/customer-reviews/assessments?status=ALL").header("Authorization",bearer(account)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(reportResponse).doesNotContain("passwordHash","password_hash","codeHash","setupToken");
        mvc.perform(post("/api/v1/verification/admin-setup").header("Authorization",bearer(account)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body))).andExpect(status().isConflict());
    }
    static final AtomicInteger PHONE = new AtomicInteger(10000000);

    JsonNode register() throws Exception {
        String suffix = UUID.randomUUID().toString();
        return json.readTree(mvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("fullName","Test Saver", "email",suffix + "@example.com",
                "phone","080" + PHONE.getAndIncrement(), "password","Development123!"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
    }
    String bearer(JsonNode account) { return "Bearer " + account.path("accessToken").asText(); }

    @Test void servesInstallableShellAndProtectsWallet() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Save")));
        mvc.perform(get("/manifest.webmanifest")).andExpect(status().isOk())
            .andExpect(jsonPath("$.display").value("standalone"));
        for (String path : new String[]{"/app.css","/app.js","/sw.js","/icons/icon-192.png","/icons/icon-512.png"})
            mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/wallet")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/money/orders")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/money/webhook/flutterwave").contentType(MediaType.APPLICATION_JSON).content("{\"data\":{}}")).andExpect(status().isUnauthorized());
    }

    @Test void registrationLoginWalletAndRefreshTokenIsolation() throws Exception {
        JsonNode account = register();
        assertThat(account.path("user").path("status").asText()).isEqualTo("ACTIVE");
        mvc.perform(get("/api/v1/wallet").header("Authorization",bearer(account)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.balanceNaira").value(0))
            .andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/v1/wallet").header("Authorization","Bearer " + account.path("refreshToken").asText()))
            .andExpect(status().isUnauthorized());
        String email = account.path("user").path("email").asText();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("emailOrPhone",email.toUpperCase(),"password","Development123!"))))
            .andExpect(status().isOk());
        String phone = "0" + account.path("user").path("phone").asText().substring(4);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("emailOrPhone",phone,"password","Development123!"))))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("fullName","Duplicate", "email",email.toUpperCase(),
                "phone",phone, "password","Development123!")))).andExpect(status().isConflict());
    }

    @Test void refreshRotatesAndLogoutRevokesOnlyTheSelectedSession() throws Exception {
        JsonNode first=register();String original=first.path("refreshToken").asText();
        var renewal=mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken",original)))).andExpect(status().isOk()).andReturn();
        JsonNode renewed=json.readTree(renewal.getResponse().getContentAsString()).path("data");
        assertThat(renewed.path("refreshToken").asText()).isNotEqualTo(original);
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken",original)))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken",first.path("accessToken").asText())))).andExpect(status().isUnauthorized());
        var login=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("emailOrPhone",first.path("user").path("email").asText(),"password","Development123!"))))
            .andExpect(status().isOk()).andReturn();
        JsonNode second=json.readTree(login.getResponse().getContentAsString()).path("data");
        // A sign-out racing with rotation still revokes the correct session, never the other login.
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken",original)))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/wallet").header("Authorization",bearer(first))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wallet").header("Authorization",bearer(renewed))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wallet").header("Authorization",bearer(second))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken",renewed.path("refreshToken").asText())))).andExpect(status().isUnauthorized());
    }

    @Test void suspendedAccountCannotRenewAndExpiredServerSessionCannotAuthorize() throws Exception {
        JsonNode account=register();UUID userId=UUID.fromString(account.path("user").path("id").asText());
        var user=users.findById(userId).orElseThrow();user.setStatus(UserStatus.SUSPENDED);users.saveAndFlush(user);
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken",account.path("refreshToken").asText())))).andExpect(status().isUnauthorized());
        user.setStatus(UserStatus.ACTIVE);users.saveAndFlush(user);
        jdbc.update("UPDATE refresh_tokens SET expires_at=? WHERE user_id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),userId.toString());
        mvc.perform(get("/api/v1/wallet").header("Authorization",bearer(account))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken",account.path("refreshToken").asText())))).andExpect(status().isUnauthorized());
    }

    @Test void pendingCustomersCanCompleteIdentityButCannotUseWalletOrGroups() throws Exception {
        JsonNode account=register();
        var customer=users.findById(UUID.fromString(account.path("user").path("id").asText())).orElseThrow();
        customer.setStatus(UserStatus.PENDING_VERIFICATION);users.saveAndFlush(customer);
        mvc.perform(get("/api/v1/identity").header("Authorization",bearer(account))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/verification/email").header("Authorization",bearer(account))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/verification/email")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wallet").header("Authorization",bearer(account))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/money/orders").header("Authorization",bearer(account))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/groups/my").header("Authorization",bearer(account))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("emailOrPhone",customer.getEmail(),"password","Development123!"))))
            .andExpect(status().isOk());
    }

    @Test void emailVerifiedPendingCustomerCanLoginAndViewDashboardButCannotTransact() throws Exception {
        JsonNode account=register();
        var customer=users.findById(UUID.fromString(account.path("user").path("id").asText())).orElseThrow();
        customer.setStatus(UserStatus.PENDING_VERIFICATION);customer.setEmailVerified(true);users.saveAndFlush(customer);
        var login=json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("emailOrPhone",customer.getEmail(),"password","Development123!"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        mvc.perform(get("/api/v1/wallet").header("Authorization",bearer(login))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/verification/account-assessment").header("Authorization",bearer(login)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.customerId").value(customer.getId().toString()));
        mvc.perform(get("/api/v1/money/capabilities").header("Authorization",bearer(login)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.collections").value(false));
        mvc.perform(get("/api/v1/admin/customer-reviews/assessments").header("Authorization",bearer(login))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/groups/my").header("Authorization",bearer(login))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/verification/email").header("Authorization",bearer(login))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/groups").header("Authorization",bearer(login)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/money/orders").header("Authorization",bearer(login)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reconciliation/issues").header("Authorization",bearer(login))).andExpect(status().isForbidden());
        assertThat(users.findById(customer.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
    }
    @Test void createsAndJoinsCircleAndEnforcesCapacity() throws Exception {
        JsonNode owner = register(), member = register(), outsider = register();
        String body = json.writeValueAsString(Map.of("name","Business reserve", "contributionNaira",5000,
            "maxMembers",2, "cycleFrequency","MONTHLY", "payoutOrder","SEQUENTIAL", "startDate",LocalDate.now().plusDays(2).toString()));
        JsonNode group = json.readTree(mvc.perform(post("/api/v1/groups").header("Authorization",bearer(owner))
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.currentMembers").value(1))
            .andReturn().getResponse().getContentAsString()).path("data");
        mvc.perform(get("/api/v1/groups/my").header("Authorization",bearer(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/groups/" + group.path("id").asText()).header("Authorization",bearer(outsider)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/groups/public/open"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].inviteCode").doesNotExist());
        String join = "/api/v1/groups/join/" + group.path("inviteCode").asText();
        mvc.perform(post(join).header("Authorization",bearer(member)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentMembers").value(2));
        mvc.perform(post(join).header("Authorization",bearer(outsider))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/groups").header("Authorization",bearer(owner)).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("5000","-1"))).andExpect(status().isUnprocessableEntity());
    }

    @Test void disabledAccountsAndPaymentsCannotOperate() throws Exception {
        JsonNode account = register();
        mvc.perform(post("/api/v1/wallet/fund").param("amountNaira","1000").header("Idempotency-Key",UUID.randomUUID().toString()).header("Authorization",bearer(account)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Payments are not enabled. Provider setup is required."));
        var user = users.findById(UUID.fromString(account.path("user").path("id").asText())).orElseThrow();
        user.setStatus(UserStatus.SUSPENDED); users.saveAndFlush(user);
        mvc.perform(get("/api/v1/wallet").header("Authorization",bearer(account))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wallet").header("Authorization","Bearer malformed")).andExpect(status().isUnauthorized());
    }
}
