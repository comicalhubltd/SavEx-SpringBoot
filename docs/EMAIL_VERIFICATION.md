# Email-only contact verification

Customers with saved proof for their current email and valid name, email and Nigerian phone format now receive automatic email-only account approval. Their status becomes ACTIVE, an approval timestamp appears in the administrator report, and the dashboard opens immediately. Previously verified pending customers are checked on sign-in or email-status requests. Identity remains UNVERIFIED until the administrator reviews genuine evidence and records Verify. Invalid details remain pending; restricted accounts are never reactivated automatically. The email form is hidden after success. Live funding retains its separate identity review, allowlist and pilot checks.

Update 8 October 2026: BVN consent controls are temporarily removed from the web interface pending Flutterwave approval. Keep FLW_IDENTITY_ENABLED=false. Identity requirements for live customer approval remain enforced. Migration V14 replaces timestamp-dependent code hashes with a stable random nonce; database/JDBC timestamp precision can no longer invalidate the correct code. Outstanding unverified codes from earlier versions are invalidated, so request a new code after updating. Completed verifications and resend quotas are retained. The fix is tested against XAMPP MariaDB across application restart with mocked email delivery.

Configure MAIL_USER with the Gmail sender address and MAIL_PASS with its private app password in .env. Never use a password exposed in chat. The local launcher already loads these variables. Restart the persistent app using `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -Profile xampp`. Flyway applies V13 on persistent startup. Gmail uses authenticated STARTTLS on port 587; delivery errors do not log credentials or codes.

Sign in to SavEx, choose Send verification code, read the email and enter its six digits. Codes expire after ten minutes. Each customer may request at most five messages per rolling one-hour window anchored at the first send, with at least 60 seconds between sends. A resend replaces the earlier code. Five incorrect attempts invalidate that code. Rate limits and attempts persist across restarts; requests for one customer are serialized by a database lock. Only a keyed hash is retained, never the plaintext code. JWT secret rotation invalidates outstanding codes.

Authenticated APIs (also available to pending customers):
- GET /api/v1/verification/email
- POST /api/v1/verification/email/send (no body)
- POST /api/v1/verification/email/check with {"code":"123456"}

Read `data.verified` and `data.message` to determine the check result. A completed email check activates a valid pending account, but does not verify phone ownership or identity. Registration still collects a phone number, but SMS verification is not required by the restricted live funding policy.

The existing independent ADMIN customer-review route remains required for identity approval. Email approval requires the completed SavEx code record for the customer's current email, not just an administrator-supplied assertion. `emailOwnershipChecked` and `identityMatched` must be true; `phoneOwnershipChecked` may be false and never changes the phone flag. Provider BVN evidence must still match the payment environment. Existing approved customers must complete the code flow before any new approval; no historical phone/email flags are fabricated or altered by migration.

This release adds no automatic identity matching, SMS delivery, bulk mailing, administrator promotion or live payment activation. Delivery acceptance must be tested with a real configured mailbox; automated tests use a mocked mail sender and send no messages.
