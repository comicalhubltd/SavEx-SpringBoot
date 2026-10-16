# Airtime data utilities and subscriptions

## Implemented services

SavEx supports Nigerian airtime, mobile data, electricity utility bills, cable TV and internet subscriptions through the existing Flutterwave v3 bill catalogue. Customers choose a provider and package returned by Flutterwave; no unsupported providers, package prices or subscription brands are invented. This is a one-time customer-authorised payment flow, not recurring subscription charging. Tax, donations and unrelated catalogue categories remain out of scope.

## Activation status

Code, startup and dashboard controls now permit LIVE bills when explicitly activated. The private `FLW_BILLS_APPROVED` setting has not been enabled by this implementation. A read-only LIVE Airtime catalogue request returned HTTP 400, so account access and catalogue availability remain unconfirmed. No live bill purchase was made.

Ask Flutterwave to confirm bill-payment access for the merchant, the current bill catalogue endpoints, server outbound IP allowlisting, source balance requirements and the terminal status fields returned by verbose bill verification. A successful catalogue read alone does not prove a purchase can complete.

Only after access is confirmed, set `FLW_BILLS_APPROVED=true` in `.env`, restart SavEx using the persistent xampp profile and reload. Open Payments and select Airtime, Mobile data, Electricity, Cable TV or Internet. Keep the current LIVE customer approval and allowlist controls. The startup script no longer rejects explicit bill activation. Existing limits remain NGN 10,000 per transaction and NGN 20,000 per rolling 24 hours per service, including SavEx fees in daily usage; the overall outgoing daily cap also applies.

## Customer payment flow

1. Select the service, current provider and package. Fixed catalogue prices populate the amount; variable-price products accept a customer amount. The package's customer label is displayed where available.
2. Enter an 11-digit Nigerian phone number for airtime/data, a meter number for electricity, a decoder/smartcard number for TV, or the provider's customer identifier for internet service.
3. Review the payment. The backend checks that the provider belongs to the selected category, the item belongs to the provider, and the price has not changed. Electricity, cable and internet customer details are verified with the provider and the returned name appears in confirmation.
4. Confirm principal, SavEx application fee, total and password through the existing flow. No automatic retries or recurring debits are introduced.
5. Follow payment history. Verified success produces a receipt. Electricity tokens from `recharge_token` or textual `extra` are shown only on successful electricity orders. Some products are postpaid and do not issue a token.

## Money handling

Before order creation the backend checks current account eligibility, provider/customer/package validation and merchant NGN available funds against principal plus the provider's quoted fee. Missing funds or inaccessible provider data reject creation without reserving customer money. A snapshot is not a merchant-fund reservation; concurrent provider spending can still cause a later failure.

The customer wallet reserves principal plus the agreed SavEx fee under the existing wallet lock. Provider fees are borne by SavEx, checked during preflight and posted once as a provider-bill-fee expense after verified success. Where item and customer validation quote a fee, the larger figure is used conservatively for preflight; provider confirmation should establish the actual product fee semantics. No provider fee is silently added to customer debit. Provider fees may exceed SavEx's application fee.

Successful verification debits once. Confirmed failure releases the reserved principal and SavEx fee once. Timeouts retain funds and verify rather than submit again. Disabling bills stops new requests and queued submissions but permits verification of bills already submitted.

## Verification limitations

The saved bill `tx_ref` identifies the provider status lookup. Currency, amount, customer reference and customer identifier must match before final accounting. The returned bill transaction reference must match the saved reference where one exists. Explicit successful/failed/reversed/pending status handling remains in place.

Some Flutterwave documentation examples omit `data.status` even when HTTP and the top-level response say success. SavEx does not interpret top-level request success as proof of delivery. Responses without a terminal bill status remain pending for further verification and eventual support review. Live acceptance must establish the provider's actual response schema before enabling customer use. Acknowledgements and callback payloads alone cannot settle a wallet.

## Validation

Full Maven verify passed: 108 tests total, 106 passed, 2 skipped, no failures/errors. A subsequent isolated MoneyLedgerTest run also passed after adding the LIVE bill success/failure/activation fixture. All 24 JavaScript tests passed, and modified JavaScript and startup-script syntax checks passed. Fixtures cover fixed package prices, phone validation, customer limits, merchant-borne fees and funding, bill transaction identifiers, missing final status, electricity token visibility, LIVE reservation/debit/release and duplicate protection. No real bill purchase, live app restart or running-browser bill acceptance was performed.

Official references: [Flutterwave bill-payment flow](https://developer.flutterwave.com/docs/bill-payment), [create a bill payment](https://developer.flutterwave.com/reference/create-a-bill-payment), [verify bill status](https://developer.flutterwave.com/reference/get-status-of-a-bill-payment).
