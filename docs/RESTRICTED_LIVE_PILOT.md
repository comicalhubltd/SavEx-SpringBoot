# Restricted live collections pilot

Latest approval change: [reviewer portal and manual evidence reports](REVIEWER_PORTAL.md). BVN consent is now optional for customer approval: an independent administrator can review genuine retained identity evidence and record a report using DOCUMENT_REVIEW. That route sets REVIEWED, never BVN_VERIFIED. The BVN-specific instructions below describe the alternative consent pathway. Email ownership, independent review, a LIVE approval, allowlist and pilot limits still apply.

This release provides manual, audited customer approval; ongoing outcome review; provider-settlement matching; bank-statement evidence; and application fees. It permits a restricted wallet-funding pilot, not unrestricted wallet custody, withdrawals, bills or group payments with real money.

## Application fee

Funding, bank withdrawals and bill purchases carry a SavEx fee of 0.5%, rounded half up to the nearest kobo and capped at 50,000 kobo (NGN 500). Internal group contributions and payouts are excluded. Funding charges principal plus the fee at checkout and credits only the principal to the wallet. Outgoing payments reserve and debit principal plus the fee while sending only the principal to the provider. Failed outgoing payments release both amounts; uncertain outcomes retain both. Only verified success recognises fee revenue. Flutterwave's own processing fees remain separate merchant expenses, not an additional quoted SavEx charge.

POST /api/v1/money/quote returns amountKobo, applicationFeeKobo and totalDebitKobo. The confirmation screen displays the fee and total. POST /api/v1/money/orders requires applicationFeeKobo to equal the server calculation, and includes it in the idempotency fingerprint. The retired wallet/fund endpoint rejects fee-bearing requests so clients cannot bypass disclosure. Earlier orders retain zero application fees; fees are not applied retrospectively.

## Genuine identity and contact evidence

Pending customers may sign in only to complete identity consent. They cannot access wallets, groups, money orders or administrative routes. BVN consent initiation supports Flutterwave's redirect_url response. The provider environment is stored with identity evidence.

Customers verify email ownership through the SavEx expiring-code flow; see [email setup](EMAIL_VERIFICATION.md). Phone ownership verification is optional and is not fabricated by approval. An independent active ADMIN must check identity against the customer and retained evidence. Consent completion alone does not activate the account. No public self-approval or automatic FULL_KYC exists. A real independent administrator and provider BVN access are prerequisites; the software does not fabricate them. SMS delivery and NIN/liveness/sanctions checks are not implemented.

The administrator submits POST /api/v1/admin/customer-reviews with bearer authentication:

```json
{
  "userId": "CUSTOMER_UUID",
  "password": "REVIEWER_PASSWORD",
  "evidenceReference": "PRIVATE_RETAINED_CASE_REFERENCE",
  "emailOwnershipChecked": true,
  "phoneOwnershipChecked": false,
  "identityMatched": true,
  "approve": true
}
```

The customer must have EVIDENCE_RECEIVED in the same provider environment and a completed SavEx email-code verification record for their current email. Administrator assertions alone cannot replace that code check. Self-review and reactivation of suspended/closed customers are rejected. Approval grants ACTIVE and BVN_VERIFIED, not FULL_KYC. Evidence references must point to genuine retained case material; do not place raw BVNs or identity documents into request fields. Reviews and financial journal entries are immutable at the database layer.

## Reconciliation

The worker re-verifies successful and failed orders after initial processing, without resubmitting. Authenticated dispute/refund/reversal callbacks create review issues and suspend the customer. Changed verified outcomes, amount/reference/currency discrepancies and requests pending over 24 hours also require review. A full funding reversal is booked only after provider verification matches reference, provider transaction ID, currency and full gross amount. It removes the original wallet credit, reverses the SavEx fee through new journal postings, and cannot execute twice. If available funds are insufficient it creates a recovery exception instead of a negative balance. Unknown/partial refunds and chargebacks require operator investigation and a separately justified correcting journal workflow; this release does not initiate provider refunds.

Administrator endpoints:

- POST /api/v1/admin/reconciliation/orders/{orderId}/reverify: fetch the actual provider outcome in the matching environment and apply the verified full-reversal rule or record an exception.
- GET /api/v1/admin/reconciliation/issues: up to 100 oldest open exceptions.
- POST /api/v1/admin/reconciliation/settlements/{providerSettlementId}?page=1: fetch a provider settlement page directly, match known successful funding orders by reference, provider ID, currency and gross amount, and book the provider fee and net settlement once. Process every provider page. Missing fields are rejected; changed matches raise exceptions. Payments belonging to other merchant businesses are excluded. Actual account payload acceptance is still required.
- POST /api/v1/admin/reconciliation/orders/{orderId}/bank-receipt: supply password, netKobo and evidenceReference after checking the actual bank statement. The allocation must equal the provider gross less provider fee. Records a unique receipt and a balancing journal entry. Provider success alone is never labelled bank-confirmed.
- POST /api/v1/admin/reconciliation/issues/{issueId}/acknowledge: supply password and evidenceReference. This records operator evidence only; it does not repair balances or reactivate an account.

All these routes require an active ADMIN. The review queue is API-based; a full operations console, automatic settlement-page sweeper and bank feed are not included.

## Pilot startup

Use only the persistent xampp or prod profile. The local launcher supports xampp. Never combine dev with LIVE. Configure secrets privately and keep test and live databases separate. Orders and BVN evidence record their provider environment; UNKNOWN legacy orders and other-environment orders are never processed by the new worker.

After real customer review and provider collection approval, configure:

```dotenv
SAVEX_MONEY_MODE=LIVE
SAVEX_LIVE_PILOT_ENABLED=true
SAVEX_LIVE_ALLOWED_USERS=APPROVED_CUSTOMER_UUID
SAVEX_LIVE_MAX_TRANSACTION_KOBO=100000
SAVEX_LIVE_DAILY_LIMIT_KOBO=200000
FLW_TRANSFERS_APPROVED=false
FLW_BILLS_APPROVED=false
```

The live secret must be a v3 live key, the webhook secret at least 32 characters, and SAVEX_PUBLIC_URL a public HTTPS URL. Configure Live webhooks separately from Test webhooks. Keep DB_PASS and a private JWT_SECRET in .env. The allowlist is explicit: the application does not choose a customer or enable the pilot automatically.

Maximum principal is NGN 1,000 per payment and the rolling daily allowance is NGN 2,000 inclusive of prior fee-bearing requests. Higher configuration limits fail startup. The live worker checks approval and allowed-user membership again before sending queued funding. Withdrawals, bills and group contributions/payouts remain disabled in LIVE, though fees are implemented and tested for outgoing operations in TEST.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -Profile xampp
```

An API-key authentication check establishes access, not merchant-product approval. This implementation does not confirm business approval, evaluate customer evidence, generate a reviewer account or send a live payment during installation.

## Recovery exercise

PersistentRecoveryTest is opt-in and requires a separate savex_acceptance database. It starts and closes three application contexts, verifies retained pending orders, re-query rather than re-submit after restart, one wallet credit and one fee posting after duplicate success, and database rejection of journal edits/deletes. No provider calls are made. A graceful application restart is not a database-server crash, backup restoration or independent security review.

Run the full suite with SAVEX_PERSISTENCE_TEST=1, SAVEX_ACCEPTANCE_DB_URL targeting savex_acceptance, and DB_USER/DB_PASS supplied privately. The migration account requires TRIGGER privilege for immutable audit records. Separate migration and runtime permissions before general production use.

