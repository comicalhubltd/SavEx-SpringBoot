# Flutterwave activation and SavEx live-service plan

Reviewed 25 September 2026. This guide is based on the screenshot supplied by the owner and Flutterwave's public documentation. It is not an inspection of the signed-in account.

## What your screenshot establishes

The dashboard explicitly says it is a test environment and that live transactions become available after full activation. The merchant ID and test merchant ID do not establish live approval. The empty transfers page only means there are no transfers shown for that filter/time range.

Do not switch SavEx to real-money operation just by replacing a test key. SavEx still rejects LIVE mode while its production controls and acceptance checks remain incomplete.

## Steps for the account owner

1. Complete the business activation checklist in Flutterwave. Use the checklist for your registered business type; incorporation/business registration, owner/director identity, address and other requested evidence vary by entity. Resolve pending checks and obtain activation confirmation.
2. Describe SavEx's actual activities to Flutterwave: customer wallet funding, savings-circle contributions, group payouts, bank withdrawals, and Nigerian bill purchases. Ask which are approved for your business, whether separate approvals or partner arrangements are required, and which customer verification APIs are enabled. General account activation does not establish approval for every product.
3. Confirm live collections, NGN transfers, bills and BVN access separately. Obtain the approved NIN integration specification; the current BVN flow is not standalone NIN verification.
4. For transfers, enable transfers via API where required. For transfers and bills, arrange a funded source balance and whitelist the production server's outbound IP address. A developer laptop with a changing address is not the production host.
5. Once the production integration is ready and access is confirmed, use the dashboard's live environment and Settings / API Keys to obtain the v3 credentials. Enter secrets directly into the hosting platform's secret manager. Never send secret keys or verification codes in chat.
6. Configure the public HTTPS webhook URL and matching secret hash. Enable webhook retries. The SavEx endpoint is https://YOUR-APP-DOMAIN/api/v1/money/webhook/flutterwave (replace the placeholder with the deployed domain). localhost cannot receive Flutterwave's public callbacks.
7. Complete a controlled live acceptance exercise only after the software gates below are resolved. Compare each result with the provider transaction, settlement, customer wallet and ledger. Do not treat a successful checkout redirect as settlement.

Official references:
- [Nigeria business onboarding requirements](https://www.flutterwave.com/us/support/onboarding/onboarding-requirements-for-using-flutterwave-in-nigeria)
- [API environments and authentication](https://developer.flutterwave.com/docs/authentication)
- [Nigerian transfer prerequisites](https://developer.flutterwave.com/docs/nigerian-bank-account-transfer)
- [Nigerian bill payments](https://developer.flutterwave.com/docs/bill-payment)
- [Webhook configuration](https://developer.flutterwave.com/docs/webhooks)

## Deployment mapping

SavEx currently uses Flutterwave v3 server-side bearer authentication. Do not mix v4 OAuth credentials or v4 webhook signatures into this adapter.

| Setting | Production intention |
| --- | --- |
| FLW_SECRET_KEY | Live v3 secret supplied through the server secret manager, after approval |
| FLW_WEBHOOK_SECRET | Strong random value matching the dashboard webhook secret |
| SAVEX_PUBLIC_URL | Public HTTPS SavEx address |
| FLW_TRANSFERS_APPROVED | Enable only with confirmed transfer access and completed acceptance |
| FLW_BILLS_APPROVED | Enable only with confirmed bill access and completed acceptance |
| FLW_IDENTITY_ENABLED | Enable only with confirmed access; this alone does not mark customers verified |
| SAVEX_MONEY_MODE | Keep OFF/TEST now; LIVE intentionally fails startup pending a separately reviewed production release |

Use the production profile, persistent MySQL, durable backups and a stable outbound server IP. Keep live and test credentials, databases and callback configuration separate. Java does not load .env automatically.

## New bill services in this source version

The payment form adds electricity, cable TV and internet subscriptions, alongside airtime and mobile data. Providers and packages come from Flutterwave's Nigerian catalogue rather than a hard-coded promise of operator availability.

Electricity uses UTILITYBILLS; cable uses CABLEBILLS; internet uses INTSERVICE. The customer supplies a meter, decoder/smartcard or provider account identifier. Before confirmation, SavEx requests the provider's customer name. The server repeats validation before creating the payment, checking the biller, item, customer, fixed price and provider amount limits.

Products with a nonzero validation fee are currently rejected: separate fee quotation, authorisation and accounting must be implemented first. Unknown or incomplete validation responses also fail closed.

Electricity recharge tokens returned by the provider are stored with the order and appear only to its owner after verified SUCCESS. Tokens are never invented. Missing token data requires support follow-up. The exact terminal-status and token payloads must be tested with approved biller fixtures before release; an API response whose top-level status is success is not sufficient evidence of bill delivery.

Migration V5 adds the receipt field. New order kinds are ELECTRICITY, CABLE and INTERNET. They share the existing reservations, idempotency, daily debit limits, history and verification worker.

## Software gates before real money

The fuller release checklist is in [FLUTTERWAVE_RELEASE.md](FLUTTERWAVE_RELEASE.md). Remaining work includes transaction authorisation and verified customer identity; provider fees, settlements, reversals/refunds/chargebacks and late outcomes; an audited support workflow; production database recovery; monitoring and security validation. Provider account activation does not replace this work.

The bills extension is code for test integration, not evidence of live readiness. No payment or transfer has been sent from your account as part of this change.

## Validation of this change

The isolated Maven verification build passed 20 Java tests with no failures or errors. JavaScript syntax checking and all 8 API-client tests passed. Bill tests cover customer rejection, mismatched accounts, catalogue membership, changed prices, provider amount limits, unquoted fees and token visibility. These are local fixtures; provider-account, live-money and MySQL migration acceptance are still required.

