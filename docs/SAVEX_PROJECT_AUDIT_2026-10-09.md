# SavEx project audit — 9 October 2026

## Executive assessment

SavEx has a substantial wallet and payment foundation, with live deposit records, embedded checkout, email verification, separate administrator review, integer-kobo accounting, idempotency and recovery safeguards. It is **not ready for an unrestricted public wallet launch**. The application currently permits a restricted live funding pilot only. Withdrawals and bills are explicitly disabled in configuration and rejected by live-mode validation; enabling a flag alone is not a supported activation path.

The immediate priorities are resolving the existing reconciliation exception safely, validating payment-attempt handling, establishing settlement and bank reconciliation, confirming the licensed-partner/custody and customer KYC arrangements, and completing controlled transfer and bill acceptance testing. A live merchant account and a positive provider balance do not establish these capabilities.

No source code, credentials, database records or payment settings were changed during this audit. No real-money transaction was initiated. Only this report and isolated test/build outputs were created.

## Scope, method and limitations

Inspected Java/Spring Boot source, static HTML/JavaScript client, Maven configuration, database migrations, payment and verification services, security configuration and existing tests. Stack: Java 21, Spring Boot 3.5.16, Maven, JPA, Spring Security/JWT, Flyway, MySQL-compatible storage (local XAMPP/MariaDB), H2 tests, plain JavaScript frontend/PWA. Mobile application parity was not validated.

Executed Maven `verify` in `.build/project-audit`: **57 tests, 55 passed, 2 skipped, zero failures/errors; build successful**. Executed both JavaScript test suites: **10 passed**. These establish covered behavior against test fixtures and H2, not end-to-end live provider acceptance. Persistent recovery tests were not opted into because they would write database records. Evidence: `audit-test-run.log`, `.build/project-audit/surefire-reports/`, `scripts/api-client.test.cjs`, `scripts/inline-checkout.test.cjs`.

Read-only SQL and authenticated provider GET requests were performed. Credentials and personal customer information are omitted. Historical screenshots are corroborating evidence, not independent current API verification. Flutterwave dashboard permissions, contractual approvals, bank statements, current whitelist and external compliance documents were not independently inspected.

## Current measured evidence

| Check | Result | Interpretation |
|---|---|---|
| Local payment configuration | LIVE; restricted pilot enabled; transfers=false; bills=false; BVN=false | Current service is funding-only |
| Database migrations | V16 latest; V14–V16 successful | Migration history present; not a backup/restore test |
| Customers | 4 records: 2 active, 1 pending, 1 suspended | One customer restriction remains |
| Payment orders | 3 SUCCESS deposits; 1 REVIEW deposit | No persisted successful withdrawals/bills in this database |
| Wallets | 4; aggregate ₦800; ₦0 locked; no negative or over-locked balances | Snapshot checks pass |
| Journal | 3 principal postings totaling ₦800; 3 fee postings totaling ₦4 | Aggregate wallet principal agrees with deposit journal snapshot |
| Reconciliation | 1 OPEN VERIFIED_DETAILS_MISMATCH | Existing unresolved financial exception |
| Settlement records | 0 matches; 0 bank receipts | Provider/bank reconciliation has not been evidenced |
| Database triggers | 6 | Immutability controls exist; destructive trigger tests not run on live data |
| Flutterwave GET /v3/balances | NGN available ₦490; ledger ₦786.69 | Merchant-wide balances; not a customer's funds or permission proof |
| Flutterwave GET /v3/banks/NG | success; 703 returned records | Bank-list access works; individual bank resolution not tested |
| Implemented biller category endpoints | AIRTIME HTTP 400; data/electricity/cable/internet HTTP 403 | Actual catalogs/products remain unverified; status alone does not distinguish entitlement, endpoint incompatibility or edge blocking |

The ₦800 customer-wallet total and provider balances cannot be treated as reconciled. They measure different liabilities/assets and may include fees, settlements or unrelated merchant activity. A transaction-to-settlement-to-bank bridge is required before drawing a solvency conclusion.

## Implementation status

Paths below are relative to `C:/Users/savex/Documents/SavEx-SpringBoot/`. Complete means the stated narrow function has supporting evidence, not that the entire product is production-certified. Partial means some implementation exists. Missing means no implementation was found in the inspected project. Unverified means external or runtime evidence is unavailable.

| Module | Status | Evidence | Outstanding work | Priority |
|---|---|---|---|---|
| Wallet management | Partial | `money/MoneyLedger.java` under `src/main/java/com/savex/`; Wallet entity; ledger tests; SQL totals | Per-wallet ongoing reconciliation, pending-balance presentation, operational recovery | High |
| Wallet funding | Partial | 3 successful database deposits; InlineCheckoutService; callback and credit-once tests | Independent re-verification and settlement evidence; multi-attempt acceptance | High |
| Flutterwave configuration | Partial | MoneySettings; LIVE environment guards; successful balance/bank GETs | Verify dashboard settings, service-specific approvals and permanent hosting | High |
| Static virtual accounts | Missing | No issuance/lifecycle service found by source search | Decide product need; provider approval; ownership, expiry and webhook reconciliation | Medium |
| Payout subaccounts | Missing | No creation/mapping service found | Confirm architecture with provider; implement only if needed for agreed custody model | High |
| Bank transfers/withdrawals | Partial | Gateway transfer submission/status code; success/failure ledger tests | LIVE deliberately prohibited; provider access, whitelist, fees, recipient checks, end-to-end tests | High |
| Nigerian banks/account resolution | Partial | Bank GET successful; gateway resolve method | Real controlled resolution response and destination binding acceptance | High |
| Airtime/data | Partial | BillService categories, catalog/package price checks; tests | Current catalogs inaccessible; verify MTN/Airtel/Glo/9mobile separately | High |
| Electricity/cable/internet | Partial | Customer/product validation and payment/status paths | Provider activation, real catalog validation, tokens/receipts and refund acceptance | High |
| 0.5% service fee | Partial | ApplicationFee; quote/confirmation; fee journal; fee tests | Rate/cap/minimum admin configuration, VAT policy, product-specific pricing | High |
| Webhook processing | Partial | Constant-time verif-hash comparison; webhook only wakes verification | Current public delivery and contract test, durable ingress/event audit, operational alerting | High |
| Transaction status/idempotency | Partial | Saved orders, unique posting keys, locks, duplicate/recovery tests | Multiple provider attempts for one tx_ref; restart tests against persistent storage | High |
| Provider/bank reconciliation | Partial | ReconciliationService; settlement and reversal tests | Existing OPEN issue; zero live settlement/bank evidence; daily automation and safe resolution workflow | High |
| Authentication/email | Partial | BCrypt/JWT; expiring persisted email codes; tests | Login abuse controls, reset flow, refresh/revocation lifecycle and admin MFA | High |
| Admin/customer reports | Partial | CustomerReviewService, AccountAssessmentService, admin.html; review tests | Evidence quality, review operations, retained-document policy and restricted recovery | High |
| KYC/AML/compliance | Partial | Manual document-review attestations; optional disabled BVN | Independent identity evidence, tier limits, screening/monitoring and legal partner confirmation | High |
| Provider NGN balance visibility | Complete (narrow scope) | TransferBalanceService tests; live GET success; customer/admin controllers | Positive status does not promise any withdrawal can complete | Medium |
| Disputes/refunds | Partial | Dispute freezes and verified full deposit reversal | Partial refunds, customer support workflow, chargeback evidence/deadlines and resolutions | High |
| Receipts | Missing (customer receipt artifact) | History exists; no dedicated receipt issuance found | Durable receipt with references, fees, status and bill tokens where applicable | Medium |
| Production operations | Partial | Production profile, migrations, health and packaged build | Deployment, backups/restore, metrics/alerts, penetration/load testing and incident procedures | High |
| External approvals/licensing | Unverified | User reports live merchant verification; no agreements inspected | Written service/custody/KYC responsibilities and regulatory review | High |

## Findings and recommended fixes

### 1. Financial correctness and reconciliation

Completed within test scope: backend independently verifies outcomes; browser callbacks do not credit funds; locks serialize spending; retry keys and posting keys prevent duplicate settlement; uncertainty preserves holds; verified failure releases principal and service fee; full funding reversal uses compensating entries. See `MoneyLedgerTest` and `WalletFundingTest`.

The journal models each balanced movement as debit account, credit account and amount in a single immutable posting. This is a double-sided accounting representation, but it is not a complete finance operations system: there is no demonstrated daily trial balance, per-customer liability-to-wallet comparison, opening balance audit or independent asset coverage report.

**Critical existing issue:** one order is in REVIEW and its customer is suspended. `ReconciliationService.issue()` suspends the customer; `acknowledge()` records acknowledgment but does not repair the order, adjust accounting or reactivate the customer. Do not bypass this with a direct status edit. Build an evidence-led resolution workflow with compensating postings and explicit authorization only after the actual discrepancy is understood.

**High-risk payment-attempt handling:** `FlutterwaveGateway.verify()` verifies deposits by tx_ref; `ReconciliationService.terminal()` additionally compares the stored provider transaction ID. Historical provider screenshots show failed attempts and a successful attempt sharing a tx_ref. The code can flag a different attempt as a mismatch. This is a credible mechanism, **not a confirmed root cause of the existing issue**. Reproduce with provider fixtures and verify the successful attempt by its transaction ID while retaining exact order reference, amount and currency checks. Never relax these checks simply to unblock an account.

Settlement matching and bank receipt accounting exist, but the current database has neither. Schedule daily reconciliation with exception aging and verified bank statement allocation. Keep merchant fees, customer liabilities and service-fee revenue separate. Relevant files: `money/ReconciliationService.java`, `FlutterwaveGateway.java`, `MoneyWorker.java`, `MoneyLedger.java`, `ReconciliationController.java`, migrations V11/V12.

### 2. Withdrawal activation

Current blockers are both product configuration and code. `MoneySettings.java:67` rejects LIVE when transfers or bills are approved; MoneyController capabilities and MoneyLedger impose further live restrictions. Do not just change `FLW_TRANSFERS_APPROVED` to true: startup can fail and the remaining gates would still need deliberate changes.

Before implementing activation, confirm API transfer entitlement, the server's stable outbound IP whitelist, NGN transfer balance and pricing. The earlier whitelist screenshot is time-specific; a local ISP address may change. Ngrok inbound webhook routing does not define the outbound IP Flutterwave sees. Bank withdrawal must reserve principal plus authorized fees, submit once, persist the provider ID, verify asynchronous results and release holds only on confirmed failure. Test success, decline, timeout, duplicate, reversal, dispute and restart against sandbox/persistent storage before a separately authorized small live acceptance transaction.

The new customer balance indicator should remain qualitative; showing merchant-wide exact balances to customers would misrepresent their individual entitlement. It currently does not expose those exact amounts.

### 3. Fees and bills

`ApplicationFee.java` calculates 0.5% using decimal arithmetic, rounds half up to kobo and caps at ₦500. ₦20,000 produces ₦100. The rate and cap are hard-coded; fee enablement is configurable but the requested secure administrator fee editor, configurable minimum/maximum and versioned pricing are missing. Existing quote and ledger code separates service fees from later provider fees. Failed outgoing transactions release the fee hold; a verified full deposit reversal reverses service-fee revenue.

VAT is not separately implemented. Provider VAT/fees should not be guessed or silently treated as SavEx revenue. Confirm applicable tax and contractual pricing, then show the full authorized debit and store a pricing snapshot. Consider separate airtime/data/bill pricing because provider commissions and charges differ; commercial terms are currently unverified. BillService deliberately rejects products returning an extra provider fee rather than silently charging it; support an explicit authorized fee quote before enabling such products.

Dynamic categories and product/customer validation exist. Live bill catalogs could not be validated. The AIRTIME 400 response warrants checking the exact category/API contract; the 403 responses need provider diagnosis. Neither proves all requested networks/products are enabled. Missing end-to-end evidence includes bill fulfillment, electricity tokens, provider-specific refunds, receipts and long-pending recovery. Files: `ApplicationFee.java`, `BillService.java`, `FlutterwaveGateway.java`, `MoneyController.java`, `src/main/resources/static/app.js`.

### 4. Identity, account approval and security

Email verification proves email ownership; format checks and email-only ACTIVE status do not verify identity. Automated assessments preserve this distinction, and manual approval requires an independent active admin, password, current email evidence and an evidence reference/report. Tests cover these controls. BVN is disabled and manual document review does not require it. Whether this is sufficient for the planned wallet tiers must be agreed with the licensed partner; software approval is not regulatory approval.

Evidence reports are operator attestations, not proof that original documents were authentic or retained lawfully. Define genuine document checks, access-controlled storage, retention/deletion, consent and audit responsibilities. BVN consent access alone does not cover sanctions, AML, liveness or NIN obligations.

Security strengths: server-side authorization, current database account restrictions, password hashing, protected admin setup, code expiry/resend/attempt limits, no client-held Flutterwave secret, backend payment verification and immutable financial evidence controls.

Gaps: no login rate-limiting implementation found; refresh tokens are issued but no refresh/logout/revocation controller lifecycle was found; no demonstrated admin MFA, broad AML/sanctions monitoring, penetration test, secrets-manager deployment or tested retention/incident policy. Inspect browser token storage/XSS exposure and add a compatible security-header/CSP policy for the checkout integration. Current deployment TLS, encryption at rest, backups and restore capability are unverified. Historical credential exposure requires owner-led remediation; credentials were not rotated during this audit.

Files: `service/AuthService.java`, `controller/AuthController.java`, `security/`, `verification/EmailVerificationService.java`, `AccountApprovalService.java`, `money/CustomerReviewService.java`, `AccountAssessmentService.java`.

### 5. Webhooks and operations

The implemented `verif-hash` scheme matches the documented Flutterwave v3 contract; it should not be called incorrect solely because another API generation uses different signature headers. Bind production acceptance tests to the exact API contract. Replay protection for financial credit relies on backend verification, order state and unique journal keys. Durable event ingestion/traceability and dispute-notification replay handling still need operational validation. Historical HTTP 200 delivery proves one earlier delivery, not current reachability or correct accounting.

Embedded checkout uses the Flutterwave client SDK while keeping payment outcome verification on the backend. JavaScript tests cover duplicate callbacks and stale callbacks after sign-out. This avoids navigating to a separate hosted checkout in the normal flow; it does not make SavEx the card processor or remove provider/bank authentication requirements.

Production hosting must replace reliance on a developer laptop/tunnel. Require TLS, stable egress, restricted database access, reproducible deployment, tested restore, centralized redacted logs, queue/exception alerts, reconciliation metrics and an incident/support runbook. H2 passing tests do not establish MariaDB concurrency/recovery behavior or capacity under many simultaneous customers.

## Completion estimate

For planning only, the 20 rows in the status table score Complete=1, Partial=0.5, Missing=0 and Unverified=0. There are **15 Partial, 1 Complete, 3 Missing and 1 Unverified: 8.5/20 = 42.5%** evidence-adjusted completion against the requested full product scope. Unverified scores zero only because it contributes no verified completion; it does not mean the feature is absent. This equal-weight estimate is not a probability of safety or a production readiness percentage. Financial and regulatory blockers outweigh this score.

## External confirmations required

- Flutterwave transfer access, stable-IP whitelist, transfer pricing/limits and settlement arrangements.
- Live bill catalog/product entitlements and the actual account/API contract; investigate current 400/403 results.
- BVN consent request if that service is later used; remain disabled until approved.
- Virtual accounts/subaccounts capability and customer fund custody model if required by the agreed design.
- Written licensed-partner arrangement, safeguarding/settlement ownership, KYC tier rules, AML reporting, customer complaints/disputes, data-protection and tax responsibilities. COMICAL HUB LIMITED's independent authorization to hold customer funds is **unverified**.

CBN's BVN page links the mandatory BVN or NIN circular for Tier 1 wallets/accounts; email-only account activation cannot be assumed to satisfy the applicable identity obligations. Obtain qualified compliance review for the exact partner-led model rather than equating merchant verification with wallet licensing.

## Prioritized roadmap and launch checklist

1. **P0 — financial exceptions:** independently establish the REVIEW order's true outcome; reproduce multi-attempt reference behavior; design controlled resolution/reactivation; reconcile all three successful deposits to provider settlements and bank statements.
2. **P0 — legal/service gates:** document licensed-partner and custody scope, KYC tiers/AML duties, transfer and bill approvals. Preserve disabled services until the requirements are evidenced.
3. **P1 — withdrawal implementation:** replace collection-only gates through a reviewed activation design; authorize transparent provider fees; stable IP, account resolution, transfer verification, reservations and recovery.
4. **P1 — security/operations:** login abuse controls, admin MFA, token lifecycle, monitoring, backups/restore, privacy policies, dispute operations and permanent hosting.
5. **P1 — fee administration:** versioned secure fee settings, min/max, category pricing and applicable tax treatment, with audit trail and quote consistency.
6. **P2 — bill fulfillment:** fix/confirm catalogs, activate approved products, fee quotes, tokens/receipts/refunds and pending-state support.
7. **P2 — optional product expansion:** virtual accounts/subaccounts only after architecture/partner agreement; validate savings-circle live money flows separately.

Acceptance checklist before public launch:

- [x] Current isolated Java and JavaScript suites pass.
- [x] Read-only snapshot: no invalid wallet balances; aggregate deposit journal agrees with wallet total.
- [ ] Per-wallet ledger/liability and provider/bank reconciliation with zero unexplained differences.
- [ ] Existing OPEN mismatch resolved with evidence, immutable accounting and authorized recovery.
- [ ] Multiple-attempt, duplicate-webhook, stale-event, outage and late-success tests.
- [ ] MariaDB restart/concurrency/recovery and backup restoration tests on a disposable database.
- [ ] Provider-approved withdrawal success/failure/pending/reversal tests and separately authorized live acceptance.
- [ ] Actual bill catalogs and each supported network/product validated; receipts/tokens/refunds tested.
- [ ] Fee, provider charge and VAT disclosures approved; failed fees and partial refunds covered.
- [ ] KYC/AML/licensed-partner/custody and data-protection obligations signed off.
- [ ] Security assessment, rate controls, token lifecycle/admin MFA and load tests completed.
- [ ] Production TLS/stable egress, alerts, runbooks, support and ongoing reconciliation operational.

**Launch decision: no unrestricted public launch yet.** Even the restricted pilot requires the unresolved financial exception and reconciliation coverage to be actively managed. Present and approve a scoped implementation plan before changing source, schema, credentials or live service settings.

## Primary references

- Flutterwave v3 webhook authentication: https://developer.flutterwave.com/v3.0.0/docs/webhooks
- Flutterwave webhook handling and independent verification: https://developer.flutterwave.com/docs/webhooks
- CBN payment service provider frameworks: https://www.cbn.gov.ng/PaymentsSystem/PSPs.html
- CBN BVN framework and Tier 1 wallet/account circular links: https://www.cbn.gov.ng/PaymentsSystem/BVN.html
