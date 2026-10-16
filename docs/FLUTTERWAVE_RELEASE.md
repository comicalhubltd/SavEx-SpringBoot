# SavEx: Nigeria/NGN payment release

See [account activation and live setup](FLUTTERWAVE_LIVE_SETUP.md) for the screenshot review and provider setup steps.

## Current release status

This build adds executable **test-mode** Flutterwave v3 integrations and a payment ledger. It is not a production financial service. Payments default to OFF. LIVE mode deliberately fails startup while the launch work below remains incomplete. Your collections/transfers/bills approval status was reported as test/unknown; Flutterwave identity access was reported as approved.

| Capability | Implemented | Remaining validation |
| --- | --- | --- |
| Hosted wallet funding | Persistent request, hosted checkout, reference re-query, exact NGN amount matching, one credit per order | Your Flutterwave test account, settlement fees, chargebacks, reconciliation |
| Bank withdrawals | Bank catalogue, account-name lookup, password confirmation, held funds, asynchronous transfer and status query | Beneficiary ownership/name matching, transaction OTP, live transfer approval |
| Airtime/mobile data | Provider billers and package catalogue, server-side package price checks, phone validation, purchase/status adapters | Approved bill access; actual MTN/Airtel/Glo/9mobile availability; verbose terminal-status response fixtures |
| Electricity, cable TV, internet | Live catalogue lookup in test integration, customer validation before confirmation and again on server, price/limit checks, receipt token storage | Approved bill access; terminal outcome and token fixtures; fee-bearing products intentionally blocked |
| Circle contributions | Membership, due date, exact amount, one contribution per member/cycle, wallet debit and group ledger posting | Live contractual terms, arrears/disputes, freeze/cancellation handling |
| Circle payouts | Atomic wallet credit after every member funds the cycle; organiser first then joining order | Production reconciliation and operational recovery; bank cash-out is a separate withdrawal |
| BVN | Flutterwave hosted consent, owned request reference, evidence retrieval, no raw BVN saved | Account fixture tests; identity matching, fraud/liveness and review process |
| NIN | Requirement recorded; collection withheld | Obtain approved NIN verification endpoint/specification from Flutterwave; BVN response containing NIN is not standalone NIN verification |
| International | NGN-only guard | Country-by-country approval, currencies, FX quotes, settlement and regulatory work |

No live transactions were sent. Automated tests use local fixtures, not provider credentials.

## Architecture and guarantees

Java 21 / Spring Boot owns business rules, validation, provider calls and persistence. The responsive PWA uses same-origin JSON APIs and works in phone/desktop browsers and VS Code. Money amounts are integer kobo internally; Flutterwave amounts use decimal naira with exact conversion.

Each payment has a caller idempotency key, a server reference and a fingerprint of its financial fields. Reusing a key for a different instruction fails. Orders survive provider timeouts in persistent deployments. An HTTP timeout does not release a withdrawal or bill reservation and does not cause a new submission. The worker re-queries the existing reference. A checkout redirect does not credit a wallet.

Balance changes take database row locks and create balanced debit/credit journal pairs in the same transaction. Group payouts consume group escrow after all members contribute. The journal is append-only through application workflows; production database privileges still need to enforce immutability.

Flutterwave v3 notifications use the documented verif-hash header. It is checked with constant-time comparison. Webhook content only schedules verification; financial values are fetched from Flutterwave. V4 HMAC signatures are a different contract and are not silently accepted.

Unknown or mismatched terminal responses enter REVIEW with held funds intact. This is intentionally conservative. A bill status response that omits its payment outcome cannot prove delivery merely because the HTTP/API request succeeded.

## Local test configuration

Set these variables in the environment of the Java process, using Flutterwave **test** credentials. Never put secrets in committed files, frontend JavaScript, screenshots or chat.

- SAVEX_MONEY_MODE=TEST
- FLW_SECRET_KEY: your Flutterwave v3 test secret
- FLW_WEBHOOK_SECRET: a random secret of at least 32 characters, matching the dashboard webhook hash
- SAVEX_PUBLIC_URL: your application's public HTTPS URL for provider redirects (localhost is useful only for local browser return testing)
- FLW_TRANSFERS_APPROVED=true only when transfer access is enabled for your test account
- FLW_BILLS_APPROVED=true only when bills access is enabled for your test account
- FLW_IDENTITY_ENABLED=true when the BVN service is enabled

The webhook URL is /api/v1/money/webhook/flutterwave under your HTTPS application domain. Enable provider webhook retries. The development H2 database is disposable: restarting it loses test users, balances and pending requests. Use persistent MySQL for restart/recovery validation. Do not connect real-money credentials to development mode.

In VS Code, run the existing Java task after setting environment variables. The application remains OFF without these settings. An .env file is not automatically loaded by Java; use the launcher environment or a configured secret manager.

## API reference

All paths are under /api/v1. Except the provider webhook, the following require an active authenticated user.

- GET /money/capabilities: OFF/TEST mode and enabled payment functions.
- POST /money/orders: Idempotency-Key header (16–80 letters, digits, hyphens or underscores); JSON kind, amountKobo, currency=NGN, and operation fields.
- GET /money/orders: latest 50 owned requests; no other customer's history.
- GET /money/banks and POST /money/resolve-bank: Nigerian bank list and account-name lookup.
- GET /money/billers?category=AIRTIME, MOBILEDATA, UTILITYBILLS, CABLEBILLS or INTSERVICE; GET /money/items?biller=...: provider catalogue.
- POST /money/validate-bill: bill draft without password; returns the validated customer name/account and amount. Payment submission revalidates.
- POST /identity/bvn/consent: bvn, firstname, lastname, password, consent=true.
- GET /identity and POST /identity/bvn/refresh: owned consent/evidence progress.

DEPOSIT needs an amount; WITHDRAWAL needs destination account number and serviceId bank code; AIRTIME/DATA need destination phone, serviceId biller code and variationCode item code; ELECTRICITY/CABLE/INTERNET need destination meter/account, serviceId and variationCode; CONTRIBUTION needs groupId and cycleNumber. Outgoing requests require the current password in test mode. PAYOUT cannot be created by an arbitrary client.

The legacy Paystack funding/callback paths cannot change funds. Do not re-enable them.

## Production work still required

1. Confirm provider approvals for collections, transfers, BVN and each bill product. Obtain NIN documentation/access. Validate sandbox payloads against the account actually used.
2. Implement email/phone account verification, transaction-bound one-time authorisation with attempt limits, recovery, and active-session revocation. Development auto-activation is not verification.
3. Finish identity checks, consent/retention policy, name and beneficiary matching, duplicate-identity controls, review decisions and audit. BVN EVIDENCE_RECEIVED is not FULL_KYC.
4. Add settlement, provider-fee, refund, late-success, reversal and chargeback reconciliation. Current terminal orders do not automatically handle later disputes/reversals. Build an audited support/reconciliation queue with alerts for aged pending/REVIEW requests; do not edit balances manually.
5. Validate the Flyway migrations against MySQL; test persistence across crashes and restarts, backup restore, worker leases, provider outages and scale. Locking tests against H2 alone are not MySQL proof.
6. Add rate limits, operational monitoring, least-privilege database access, encryption/key rotation, TLS, privacy controls and independent security review.
7. Agree the legal/operating model for group savings and wallet custody with qualified local advisers and approved partners; provider API access alone does not authorise every financial product.
8. Complete a controlled live acceptance programme with approved accounts, real settlement evidence and reconciled ledgers, then review and remove the LIVE startup lock in a separately tested change.

## Revenue opportunities

Start with recurring Nigerian usage: savings circles, small-business expense organisation and convenient airtime/data purchases. Add international corridors only after the Nigerian operation reconciles reliably.

- **Bill commissions:** negotiate operator/aggregator commissions; recognise only confirmed earned commission. Revenue is not the whole recharge amount.
- **Premium circle administration:** optional subscriptions for exports, reminders, multi-organiser controls, contribution reporting and business dashboards. Keep core balances and statements accessible.
- **SME services:** paid invoicing, payment reminders, collection reporting and accounting exports; pricing should cover provider costs, support and dispute losses.
- **Transparent service fees:** quote a separately itemised fee before approval, persist that quote, and reconcile fees and taxes. This implementation does not yet charge SavEx fees.
- **Partner referrals:** approved insurance or other partner products with consent and clear disclosure. Avoid lending or investment-return promises before separate product approval.

Track net contribution margin per successful transaction: earned fee/commission minus provider charges, identity cost allocation, support, fraud/dispute losses and applicable tax. Also measure repeat usage, retained paying groups, failed-payment rates, unresolved reserves and support cost. Do not count customer wallet balances or group principal as company revenue.

## Primary integration sources

Reviewed 25 September 2026:
- [Hosted checkout](https://developer.flutterwave.com/reference/checkout)
- [Verify by merchant reference](https://developer.flutterwave.com/reference/verify-transaction-with-tx_ref)
- [Nigerian bank transfers](https://developer.flutterwave.com/docs/nigerian-bank-account-transfer)
- [Transfer reference lookup](https://developer.flutterwave.com/reference/get-all-transfers)
- [Bills and activation prerequisites](https://developer.flutterwave.com/docs/bill-payment)
- [Bill payment creation](https://developer.flutterwave.com/reference/create-a-bill-payment)
- [Bill status lookup](https://developer.flutterwave.com/reference/get-status-of-a-bill-payment)
- [V3 webhook authentication and verification](https://developer.flutterwave.com/docs/webhooks)
- [BVN consent](https://developer.flutterwave.com/docs/bvn-verification)
- [Retrieve BVN evidence](https://developer.flutterwave.com/reference/retrieve-bvn-information)

## Validation completed — 25 September 2026

- Isolated Maven verification and executable packaging: **passed**.
- Java tests: **15 passed, 0 failures, 0 errors** (3 provider-adapter tests, 8 ledger/group tests, 4 web/authentication tests).
- Browser transport tests: **8 passed**; frontend JavaScript syntax check passed.
- The simultaneous-spending test verifies that only one debit can reserve funds when both cannot be afforded.
- Password hashing now runs before acquiring balance locks; payment creation and group contributions use READ_COMMITTED isolation.
- Build output: .build/payment-validation/savex-backend-1.0.0-SNAPSHOT.jar. The separate directory avoids sharing compiler output with the Java editor.
- No live or sandbox provider transactions were executed with customer credentials. MySQL migration validation, provider account acceptance tests, NIN integration and the production work listed above remain outstanding.

For a memory-limited Windows machine, the validated build used Maven with a 256 MB heap and test workers with a 384 MB heap, two active processors, and -Dsavex.build.directory=.build/payment-validation.
