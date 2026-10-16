# Restricted live withdrawals

The backend now supports explicitly enabled Nigerian bank withdrawals within the existing pilot. Startup, ledger creation, worker submission and customer capabilities agree on availability. Bill payments and group live payments remain disabled. BVN consent is not required by this flow; an administrator's genuine LIVE identity-evidence approval is required.

## Activation

1. Confirm Flutterwave Transfers API access, the backend's current public outbound IP allowlist, and merchant NGN transfer funds. These were not live-tested in this change.
2. Keep the existing LIVE mode, persistent xampp/prod MySQL storage, public HTTPS webhook address, webhook secret and reviewed customer UUID allowlist.
3. Set `FLW_TRANSFERS_APPROVED=true` in the project's private `.env`. Keep `FLW_BILLS_APPROVED=false`. No secret keys need changing. The implementation does not edit `.env` automatically.
4. Stop the existing SavEx process and start the updated code using `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -Profile xampp` from the project folder. Ensure XAMPP MySQL is running first. Production deployments should use their normal deployment procedure.
5. Reload the customer dashboard, open Payments, choose Withdraw, select the bank and account number, enter a small amount and confirm the recipient, SavEx fee, total and password. Only an active, email-verified, administrator-reviewed LIVE customer in the pilot allowlist with no test-history contamination can withdraw.
6. Verify the final result and recipient receipt. Do not repeat a pending withdrawal. No live transfer was initiated during implementation.

Existing limits are retained: NGN 10,000 maximum principal per transaction and NGN 20,000 rolling 24-hour usage per service (funding and withdrawals separately), with the SavEx fee included in daily usage. Reserved and pending withdrawals count towards usage. Terminal failures/reversals are excluded, subject to the existing manual-review handling for changed outcomes. The general outgoing daily debit cap also remains enforced. The available wallet balance must cover principal plus SavEx fee.

## Processing and fees

- Read-only preflight resolves the recipient on the backend, fetches the provider's NGN account-transfer fee and checks available merchant funds against principal plus provider fee. Unknown responses, HTTP 403 or insufficient provider funds reject creation before wallet reservation. Account eligibility is checked before provider calls and checked again under the wallet lock.
- This is a balance snapshot, not a reservation of merchant funds. Concurrent payouts or external merchant activity can still exhaust provider funds; provider-confirmed failures release the customer reservation. No provider error response body is shown to customers.
- Wallet reservation and final debit include principal plus the existing SavEx application fee. Provider transfer fees are borne by SavEx, stored on the order and posted once as provider-fee expense after verified success; they are not silently added to the customer debit. The current percentage application fee may be less than the provider's transfer cost.
- Submission acknowledgements remain pending. Verification fetches the saved numeric transfer ID; if the submission response was lost, it searches by the unique reference. Multiple matches require investigation. ID, reference, bank, account, principal and currency must match before completion. A missing terminal fee prevents completion until it can be confirmed.
- A timeout keeps funds reserved. Recovery verifies rather than resubmitting. Duplicate requests reuse the original order under the existing idempotency and wallet-lock checks.
- Confirmed failure releases principal and SavEx fee once. Success debits once. Mismatches and reversals go through existing review controls rather than silently refunding or altering balances. Late changed outcomes still require investigation.
- Setting `FLW_TRANSFERS_APPROVED=false` and restarting stops creation and new queued submissions. Queued funds remain reserved; already submitted transfers continue verification so disabling the feature does not strand their outcome checks. Review/cancellation of unsent reservations is an administrator follow-up, not an automatic refund.

## Validation

Full Maven verify: 90 tests total, 88 passed, 2 skipped, zero failures/errors. All 18 existing JavaScript tests passed; JavaScript and PowerShell syntax checks passed. Tests cover LIVE eligibility and limits, wallet locking/concurrent spend, timeout recovery, activation changes, exact success/failure accounting, principal and application-fee reservation/release, provider-fee expense, recipient/funding preflight, denial/redaction, exact transfer verification and duplicate provider matches. Test data uses isolated in-memory storage; no production balances or schema were changed. Running-browser and real-bank withdrawal acceptance remain pending.

Provider contracts: [get a transfer](https://developer.flutterwave.com/reference/get-a-transfer), [query transfer fee](https://developer.flutterwave.com/reference/get-transfer-fee).
