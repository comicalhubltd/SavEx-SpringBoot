# Session security update — 11 October 2026

SavEx now uses its existing `refresh_tokens` table for customer and administrator sessions. No database schema migration, payment-mode change, bank integration, credential rotation or balance correction is included.

## What changes

- Registration/sign-in creates a server-side session; only a SHA-256 digest of the refresh token is stored.
- Access and refresh JWTs have unique IDs and a session ID. Refresh tokens cannot authorize wallet/payment requests.
- `POST /api/v1/auth/refresh` accepts JSON `{ "refreshToken": "..." }`, locks the session row and rotates the refresh credential. A consumed credential cannot renew again. Current user restrictions and roles are checked.
- The server session retains its original absolute expiry (currently seven days by existing configuration); renewal does not extend that database expiry.
- `POST /api/v1/auth/logout` accepts the same body and revokes only that credential's session. All access tokens for that session then fail the database-backed access check. Other devices/logins remain active.
- For a logout racing with refresh, a still-valid signed older refresh token can revoke its own session; it cannot obtain fresh tokens or access another session. Replay detection rejects stale refresh credentials but does not automatically revoke the whole session on every replay, avoiding accidental logout from a duplicated renewal request.
- Customer and administrator pages renew proactively and before protected requests when the access token is close to expiry. Renewal is single-flight within each page. Payment requests are never automatically replayed.
- Tokens remain in page memory: no local storage, cookies, receipt URLs or service-worker credential caching. Reloading the page still requires sign-in.
- Logout clears local private views immediately and reports when server revocation could not be confirmed because of network failure.
- Authentication request limits now also cover refresh and logout, independently per endpoint using the existing configured login quota/window.
- JWT validation logs no longer include raw exception messages that might contain token-derived details.

## Compatibility and limitations

Previously issued access JWTs without a session ID remain usable until their existing short expiry (normally 15 minutes). They cannot be server-revoked through the new session table. Previously issued refresh JWTs without a session ID cannot renew; sign in again to establish a managed session.

The running application was not restarted. Use the existing restart workflow to compile these changes, then reload the customer and administrator pages. The static shell cache version was advanced to v16. No new environment variables are required.

Server-side logout prevents subsequent authorization; it cannot cancel a financial operation already authorized and in progress. Each request checks current database restrictions and session validity. Database availability is consequently required for authentication. Expired/revoked session retention and a maintenance cleanup policy remain operational follow-up work.

This update does not claim a completed Quantum MFB integration. The partnership is presently verbal; banking APIs, contractual scope, KYC/custody responsibilities and service permissions remain to be supplied and agreed. Existing live withdrawal/bill restrictions remain in place.

## Validation

Backend tests cover refresh rotation, replay rejection, access/refresh token separation, session-specific logout, other-login isolation, suspended customers and expired database sessions. Browser tests cover concurrent renewal, stale completion after logout, temporary network failure and server rejection. Payment regression tests remain part of the full build.

Validation results: Maven verify built successfully with 68 tests total, 66 passed, 2 skipped, zero failures/errors. All 16 JavaScript tests passed. Logs: `session-security-tests.log`; backend reports: `.build/session-security/surefire-reports/`. Tests use isolated H2/mock responses. Live database changes, actual provider payments, persistent recovery tests and a running-browser acceptance session were not performed.

