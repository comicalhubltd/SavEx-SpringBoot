# Payments inside SavEx

Configure FLW_PUBLIC_KEY in the private .env file with the Flutterwave v3 public key matching SAVEX_MONEY_MODE. The launcher passes this key to the backend. FLW_SECRET_KEY remains private to the backend. Restart SavEx after changing configuration.

After reviewing the principal, SavEx fee and total, confirming a funding order opens Flutterwave Inline inside the SavEx page. Name, email and phone come from the saved customer. Reference, amount including the fee and currency come from the saved backend order. SavEx does not collect card numbers, PINs or CVVs. Some payment methods require the bank or wallet's own external authentication flow; SavEx cannot prevent that requirement.

The backend marks inline orders pending and its worker verifies them instead of creating another hosted checkout. Callback and close events only request a backend status check. They never credit a wallet. The page checks the saved order for up to two minutes and refreshes the wallet after backend confirmation. Pending orders can be resumed from Recent payments with the same reference. Do not create another order while a payment remains uncertain.

Checkout preparation and status-check endpoints require authentication, ownership, the correct environment and an eligible funding account. The existing 0.5% application fee capped at NGN 500, live funding limits, allowlist and administrator identity review still apply. This change does not activate withdrawals or bills outside their configured permissions and does not resolve earlier reconciliation issues.

Implementation follows https://developer.flutterwave.com/docs/inline using a callback rather than redirect_url. Live end-to-end checkout still needs validation with the configured merchant account; automated tests do not submit real payments.
