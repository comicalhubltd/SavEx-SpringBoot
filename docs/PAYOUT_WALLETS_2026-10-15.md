# Customer payout wallets

Implemented for Flutterwave v3 NGN payout subaccounts. This is separate from settlement-splitting subaccounts and from SavEx's existing checkout wallet.

## Customer workflow

1. Restart SavEx using `scripts/start-local.ps1 -Profile xampp`. Flyway applies V18 at startup. Refresh the browser to load shell v27.
2. Sign in as an active, approved customer. Existing LIVE identity review, current email verification, allowlist and pilot controls remain enforced.
3. Open Payments & history > Your personal funding account. Confirm the account password and choose Create or retrieve funding account.
4. SavEx creates the provider wallet using the customer's stored name/email and country NG, then fetches its static NGN account. It displays the returned account name, bank and account number.
5. Send a controlled bank transfer to that account. Funding is recorded only after an independent provider transfer lookup confirms the transfer ID, destination account/bank, currency, funding marker, reference, terminal status, amount and fee.
6. Check funding and balance requests a background scan. The screen distinguishes the verified customer balance, reserved withdrawals, available customer funds, and a timestamped provider balance snapshot. Unknown provider balances are never displayed as zero.
7. For a bank withdrawal, choose Personal funding account under Withdraw from. Review the resolved recipient name, amount and SavEx fee, then confirm the payment. The provider transfer includes the saved `debit_subaccount` for that customer's wallet.

## Configuration and provider setup

- `FLW_PAYOUT_WALLETS_ENABLED=true` enables account creation and new payout-wallet withdrawals. This has been added to the private local .env file and the startup loader; .env.example defaults to false. No secrets were changed.
- Existing `SAVEX_MONEY_MODE`, secret key, public HTTPS URL, webhook secret, transfer activation, LIVE allowlist and pilot limits still apply.
- Flutterwave Dashboard > Settings > Webhooks: use the existing `/api/v1/money/webhook/flutterwave` URL and matching secret hash, select V3 webhooks and enable payout-subaccount wallet funding notifications.
- The backend must be reachable for webhooks. A stable public outbound server IP must be whitelisted for transfers. Adding an ngrok hostname does not establish a fixed outbound IP.
- Use a TEST profile/account before a controlled LIVE test. Never switch an existing customer's TEST financial history into LIVE.
- No provider wallets were created, deposits made, transfers initiated, running server restarted or existing restricted withdrawals released during implementation.

## Financial separation and fees

- Existing wallet balances and journal postings are not migrated or relabelled. Checkout deposits still fund the existing wallet and existing bill flows.
- Payout-wallet customer balances/reserves are stored separately and journalled to `psa:<wallet-id>:available/held`. The existing top-level checkout summary is unchanged.
- Funding records retain the exact provider funding reference and ID. The implemented funding calculation is gross transfer amount less the provider funding fee; no SavEx deposit fee is charged. Receipts expose gross transfer, provider fee and net wallet credit.
- Confirm this amount/fee interpretation with a small real provider statement during the controlled end-to-end test. The public reference pages do not supply all response examples. Unexpected response shapes, missing fields, ambiguous ownership, unknown terminal statuses and changed confirmed funding are not guessed or credited.
- Withdrawal SavEx fees remain the existing accepted fee policy. Provider transfer fees are booked as company expenses. Fresh provider funds must cover the transfer, pending reservations and any company-fee shortfall, without using remaining customer liabilities as company reserves.
- If provider fees exceed the SavEx fee and no company buffer exists in the subaccount, withdrawals are blocked with a company-reserve message. No automatic treasury top-up is implemented. A merchant-funded buffer needs independently identified company money; do not disguise it as a customer deposit or manually inflate customer balances.
- A higher final provider fee than the quoted fee records the actual successful payment and expense, but freezes the payout wallet for company funding/reconciliation review.
- Existing payment size and rolling 24-hour limits remain in force. Incoming LIVE funding above pilot limits is quarantined for support review rather than silently credited outside the pilot.

## Reliability and reconciliation

- Account creation is explicitly requested by the customer with password confirmation. A durable creation record is saved before the external call. A timeout cannot trigger another create call. Static-account retrieval can be retried without creating another wallet.
- Each customer has at most one local payout wallet per provider environment. Provider reference/account-number constraints prevent linking the same account to two customers.
- Signed funding notifications enqueue durable verification events. Callback amounts and balances never directly credit a customer. Duplicate notifications and repeated confirmations cannot credit twice.
- A background worker scans up to ten due wallets each minute, normally checking active wallets every five minutes, and independently verifies pending funding events. Funding history scans start at wallet creation and are capped at ten pages of 100 records. Incomplete scans are explicitly reported; missing records are not proof of failure.
- Confirmed funding transfers are periodically reverified. Changed outcomes freeze the payout wallet for review without silently reversing a customer balance. Unmatched and review events remain visible to administrators.
- Payment orders retain their funding source. Payout funding deposits are excluded from checkout verification and the checkout reconciliation import. They cannot starve the existing payment worker.
- Local wallet locks and source-specific reservations serialize concurrent withdrawals. Idempotent retries preserve the selected source. Submission acknowledgement never settles funds. Definitive failure releases the corresponding source's reserve once; timeout or restriction retains it.
- Turning off the payout feature stops new account creation/withdrawal submissions. Existing funding verification and checks of already submitted withdrawals continue while payments and workers are enabled.
- Admin > Wallet reconciliation > Personal funding accounts shows paginated customer liabilities, journal balances, provider snapshots, and recent funding-verification events. A journal match does not prove provider settlement or bank receipt.
- For an uncertain account creation, obtain the existing reference from Flutterwave. The administrator recovery form checks provider ownership and account currency, requires the administrator password, and records immutable recovery evidence. It cannot replace an active linked wallet or resolve a financial review.
- Funding reversals, merchant treasury funding, closure/deletion and migration of old balances are deliberately not automated financial corrections. Retained evidence and reconciled journals are required before those operations.

## Validation

Automated tests cover ownership/currency checks, durable uncertain creation, duplicate funding, changed funding outcomes, separate checkout balances, source-specific settlement and release, concurrent withdrawal protection, provider source routing, exact NGN balances, safe fee parsing, and browser session isolation. Existing backend and browser regression tests were also run.

The final live readiness check still requires: successful provider wallet creation/static-account retrieval; a small deposit with statement-confirmed net credit; a replayed funding notification; a supported company-fee reserve arrangement; a small bank withdrawal from the same subaccount; and provider/bank confirmation. Automated fixtures do not establish live provider access.

## Provider references

- https://developer.flutterwave.com/docs/payout-subaccount
- https://developer.flutterwave.com/reference/create-a-payout-subaccount
- https://developer.flutterwave.com/reference/fetch-static-virtual-accounts-payout-subaccount
- https://developer.flutterwave.com/reference/fetch-available-balance-payout-subaccount
- https://developer.flutterwave.com/reference/fetch-transactions-on-a-payout-sub-account
