# Automatic NGN transfer balance checks

The administrator portal shows Flutterwave's NGN available transfer balance, ledger balance, payment environment and check time. The customer dashboard shows provider funding availability alongside the customer's existing wallet balance. It does not disclose the merchant's exact balances or treat merchant funds as customer funds.

Both views check automatically on loading and every 60 seconds while the page is visible and signed in. Refresh payments or Refresh transfer balance checks again, subject to a shared backend 30-second cache. Concurrent customers share that snapshot. The backend calls only GET /v3/balances with the configured secret; no transfer or wallet change occurs. Source: https://developer.flutterwave.com/reference/get-all-wallet-balances

Administrator endpoint: GET /api/v1/admin/payments/transfer-balance. Customer endpoint: GET /api/v1/money/transfer-balance. Existing role checks restrict merchant amounts to ADMIN and customer funding status to active members/admins. API responses are not cached by browsers.

OFF mode sends no provider request. TEST balances are labeled TEST. Failed, forbidden, missing-NGN and malformed responses display UNAVAILABLE with no numerical balance, never a fabricated zero. Error bodies and keys are not returned. A positive snapshot does not confirm transfer permissions or sufficient funds for a particular withdrawal including provider fees. Live withdrawals remain subject to the separate backend funding-only restrictions until implemented and validated.
