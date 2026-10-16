# Attainable audit fixes — 10 October 2026

These additions preserve the existing Spring services, API authorization, static frontend and database schema. No migration, credential replacement, live activation or customer-balance adjustment is required.

## Included

1. **Provider attempt verification:** deposits with a saved numeric Flutterwave transaction ID use `/v3/transactions/{id}/verify` and reject a different returned ID. Initial lookup still uses tx_ref when no attempt is saved. Reference, exact amount, currency and accounting checks remain enforced. This prevents terminal rechecks accidentally selecting another attempt with the same tx_ref. It does not automatically resolve existing REVIEW orders or discover a later successful attempt after an earlier failed attempt; those still require evidence-led investigation. Endpoint contract: [Flutterwave transaction verification](https://developer.flutterwave.com/docs/transaction-verification).
2. **Configurable fees:** quote and order creation share `MoneySettings.applicationFee()`. Defaults remain 50 basis points (0.5%), minimum zero, maximum 50,000 kobo (₦500). Existing orders retain their saved fees; a stale quote for a new order is rejected. Configuration is validated at startup. There is no runtime fee-editing administrator endpoint or VAT calculation in this change.
3. **Customer receipts:** completed/reversed payments have a View receipt button in Recent payments. Receipts use backend records, mask recipients, show fees and environment, suppress reversed electricity tokens and offer Print or save PDF. No public receipt URL is introduced. These are transaction records, not VAT invoices; provider settlement charges are not added again to the displayed authorized total.
4. **Administrator reconciliation report:** the reviewer portal includes paginated wallet-to-journal checks and open payment exceptions. Available and reserved journal accounts are compared to wallet values in a repeatable-read transaction. The report makes no corrections, clears no restrictions and does not claim settlement/bank reconciliation. Legacy or opening balances without journal entries appear as differences requiring investigation.
5. **Authentication request limits:** sign-in defaults to 30 requests per 5 minutes per remote address; registration defaults to 15. Excess requests return HTTP 429 and Retry-After. State is bounded to 20,000 buckets and expires. Caller-supplied forwarding headers are not trusted by this filter. Webhooks and other endpoints are unaffected.

## Configuration

Optional environment settings (defaults require no .env edits):

| Variable | Default | Meaning |
|---|---|---|
| SAVEX_FEE_BASIS_POINTS | 50 | 100 basis points = 1% |
| SAVEX_FEE_MINIMUM_KOBO | 0 | Minimum service fee for a positive amount |
| SAVEX_FEE_MAXIMUM_KOBO | 50000 | Maximum service fee |
| SAVEX_LOGIN_LIMIT | 30 | Login requests per address/window |
| SAVEX_REGISTER_LIMIT | 15 | Registration requests per address/window |
| SAVEX_AUTH_WINDOW_SECONDS | 300 | Rate-limit window |

Changes take effect after the usual server restart. Fee edits should be made only by authorized deployment administrators, reviewed and recorded in the deployment change log. Live provider settings were not changed.

Rate limits are local to a server process and reset on restart. Multiple server instances require a shared limiter at the trusted gateway or shared storage. If a reverse proxy makes every customer appear under one address, configure trusted proxy handling at deployment and test it; do not indiscriminately trust arbitrary forwarded headers. Shared NAT users also share an address quota. These controls mitigate abuse but do not replace admin MFA or account-based threat monitoring.

## Interface/API locations

- Customer: dashboard → Recent payments → View receipt.
- Administrator: `/admin.html` → Wallet reconciliation report → Check wallets and exceptions; loaded automatically on administrator sign-in/refresh.
- Owner-only receipt: `GET /api/v1/money/orders/{id}/receipt`.
- Admin-only comparison: `GET /api/v1/admin/reconciliation/wallets?page=0`, 50 wallets per page.
- Existing admin exceptions API remains `/api/v1/admin/reconciliation/issues`.

Reports and receipts are current authenticated views, not new persisted audit snapshots. Print/export is available for receipts. Administrator report rows never include raw identity documents or credentials.

## Outstanding dependencies

Withdrawals and bills remain behind the existing service approval/live-pilot gates. Existing suspended accounts and reconciliation exceptions remain unchanged. Provider approval, licensed-partner/KYC arrangements, external settlement/bank evidence, admin MFA, shared rate limiting, token revocation, persistent recovery/load testing and public production hosting remain separate work. Virtual accounts and subaccounts are not introduced without the provider/custody design.

## Validation and using the update

- Isolated Maven verify: 66 tests total, **64 passed, 2 skipped**, no failures/errors; packaged application built successfully. Log: `audit-fixes-tests.log`; reports: `.build/audit-fixes/surefire-reports/`.
- JavaScript: **12 passed** across API transport, embedded checkout and receipt/session tests. Both application scripts pass syntax checks.
- Added coverage includes saved provider ID verification/mismatch, fee minimum/cap and stale quotes, receipt ownership/outcome/privacy, journal discrepancy detection without repair, admin API access restriction, and rate-limit expiry/header spoofing/storage bounds.
- Persistent database recovery and real provider transfers/bills were not exercised. The tests use isolated H2/mock responses and do not prove live service acceptance.
- Restart SavEx through the existing `scripts/start-local.ps1 -Profile xampp` workflow to rebuild Java and browser resources, then reload the browser. No extra credentials, database migration or environment edits are needed for the defaults. The PWA cache version was advanced so updated resources can replace the old shell.
- The running live application was not stopped or restarted as part of this change. Existing balances, restrictions and credentials were not edited.
