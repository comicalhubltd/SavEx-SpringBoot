# SavEx guided assistants

After restarting SavEx and reloading the browser, sign in and select **Assistants** in the dashboard menu.

## Savings planner

Enter a goal, savings already assigned to the goal, number of contributions and weekly/monthly frequency. The backend calculates the remaining amount and rounds each suggested contribution up to the next kobo. Contributions may exceed the remaining goal by less than one kobo per contribution due to rounding. The result is a suggestion based on user-entered amounts, with no interest assumptions, automated debits or new savings circle. A goal already met returns a zero contribution.

## Transaction helper

Load your recent transactions, select one and request an explanation. The backend fetches the transaction from SavEx and checks that it belongs to the authenticated customer. It explains the stored status and gives a next step, with a checked-at timestamp. A pending request is never presented as proof of bank receipt. The helper does not perform a fresh provider verification, infer an IP restriction, release reservations, reactivate accounts, or change transaction status. Existing administrators and background workers continue handling real verification and exceptions.

## Payment preparation

Choose Add money or Withdraw, enter the amount and select bank details where required. The backend checks current eligibility, service activation, fee-inclusive pilot usage and, for withdrawals, available wallet funds and the outgoing daily limit. It resolves the recipient through the existing bank API. Provider errors are returned as safe explanations, without response bodies.

The result is a temporary proposal. **Continue to payment review** reloads capabilities, checks the selected service and bank are still available, and fills the existing payment form. It does not submit that form or create an order. Customers must review again, provide the required password and confirm through the normal payment flow. That flow rechecks fees, eligibility, available funds, recipient/provider preflight and idempotency. No password is collected by the assistant. No proposal reserves or moves funds.

Editing proposal inputs invalidates the proposal. Responses from an earlier signed-in session are discarded. Sign-out clears proposal, account and transaction details from the assistant UI. Results and proposals are not persisted or scheduled.

## Scope

These are deterministic task assistants with guided forms, not language-model agents or natural-language chat. No model API/key is required and no customer data is sent to an external AI service. Only existing authenticated Flutterwave recipient/bank lookups are used. Bill-payment and circle-payment preparation can be added when those services are available; they are not represented as supported here. There is no new payout-subaccount integration or migration in this change.

## APIs

- `POST /api/v1/assistants/savings`: validated savings calculation; authenticated verified/member/admin access.
- `GET /api/v1/assistants/transactions/{reference}`: own-customer transaction only; authenticated verified/member/admin access.
- `POST /api/v1/assistants/payments/prepare`: proposal only; member/admin access plus current account and service checks.

All three endpoints are read-only with respect to SavEx wallets/orders. They do not inject or call ledger creation or gateway submission. No database schema changes, production balance changes, live transfers or external model calls were made during implementation.

## Validation

Full Maven verify passed: 95 tests total, 93 passed, 2 skipped. Existing 18 JavaScript checks and three new assistant UI checks passed. Additional API checks cover unauthenticated access, zero-period validation, missing own transaction and the refusal to create a payment while service mode is OFF. Running-browser acceptance remains pending; restart and reload are required to activate the new menu.
