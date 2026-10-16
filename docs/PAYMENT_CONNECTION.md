# Connect Flutterwave to SavEx

The web payment screen uses Flutterwave v3 for hosted wallet funding, Nigerian bank withdrawals, airtime, data, electricity, cable and internet bills. Services depend on the catalogue and access available to your account. Group contributions use the internal ledger.

The supplied 5 October screenshot shows the dashboard's Live mode toggle enabled. It does not supply API credentials or establish that each service is approved. SavEx currently supports OFF and TEST; LIVE remains blocked by the existing launch requirements.

## Configure locally

1. Copy `.env.example` to `.env` in the project folder.
2. In Flutterwave's test environment, obtain a **v3 test secret key** and enter it as `FLW_SECRET_KEY` in `.env`. Keep credentials out of chat and source control.
3. Set `SAVEX_MONEY_MODE=TEST` and `FLW_WEBHOOK_SECRET` to a random value of at least 32 characters. Use that same value for the dashboard webhook secret hash.
4. Set `SAVEX_PUBLIC_URL` to your reachable test address. Register `<address>/api/v1/money/webhook/flutterwave` in the dashboard. Public callbacks cannot reach localhost.
5. Enable `FLW_TRANSFERS_APPROVED` and `FLW_BILLS_APPROVED` only for services available in your test account.
6. Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1`, or the **SavEx: run** VS Code task. This launcher loads the supported `.env` settings without displaying credentials. Direct Maven and VS Code debugger launches still require process environment variables.

You can validate local configuration without starting the server using `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -CheckOnly`.

## Use the connected services

Sign in, open Payments, choose wallet funding and confirm an amount. The worker queues a Flutterwave checkout session. Refresh payment history and open **Open secure checkout**. The wallet is credited only after server-side provider verification. The checkout redirect alone does not credit funds.

Bank withdrawals require bank selection and account resolution. Bills load the provider catalogue and packages and validate customer details where required. Outgoing operations require available funds and password confirmation.

The compatibility endpoint `POST /api/v1/wallet/fund?amountNaira=125` now queues funding through the same ledger as `POST /api/v1/money/orders`. Supply bearer authentication and an `Idempotency-Key` header of 16–80 letters, digits, underscores or hyphens; reuse it for retries of the same payment. The response contains the order, initially QUEUED, rather than an immediate checkout URL. Poll authenticated `GET /api/v1/money/orders` for the checkout URL and verified status. Paystack callbacks remain disabled.

Account connectivity requires your actual test credentials and a provider acceptance exercise. No merchant-account request or real-money payment was made during this update. See `FLUTTERWAVE_RELEASE.md` for the outstanding live-release work.

