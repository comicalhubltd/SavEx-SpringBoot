# Reviewer portal and approval without BVN

Open http://localhost:8080/admin.html. The portal has a separate reviewer account creation/sign-in screen, first-administrator setup, a paginated pending-customer queue and the latest 100 immutable evidence reports. Customer dashboard access and administrator access are separate.

## First reviewer setup

1. Have the actual reviewer create their separate account in the portal, using their own name, email and password. They may also sign in with an existing separate account. Do not use the customer account they will review.
2. Send and complete the email code check in the setup panel.
3. The project owner retrieves ADMIN_SETUP_TOKEN from the private local .env file and supplies it through a private channel to the intended reviewer. Never paste this token in chat or include it in a screenshot. This secret grants administrator access.
4. The reviewer enters that token and their own password, then selects Activate reviewer administrator. The backend checks the secret, verified current email, password, account status and a database lock. Setup succeeds once only and is disabled after completion or if an active administrator already exists. Activation grants administrator access, not a customer KYC or live-money approval.
5. Sign in again. The customer queue and report form appear. Remove ADMIN_SETUP_TOKEN from .env after successful setup; its consumed database state prevents reuse even if the token remains configured.

## Automatic email-only account approval

V16 records the time of automatic email-only approval. A correct email code and valid name, email and Nigerian phone format activate a pending account automatically. The phone check confirms format only, not ownership. Identity stays UNVERIFIED and administrator privileges are not granted. Existing customers with saved proof for their current email are assessed on their next sign-in or email-status check. Suspended, closed and deleted accounts cannot be automatically reactivated. Approval is serialized under the customer database row lock so concurrent checks do not create repeated approvals.

The administrator opens the Active accounts report list, selects a customer and reads the automatically generated checks and approval timestamp. To verify identity, review genuine evidence, supply the case reference and findings, confirm the checks and password, and click Verify and record report. This records the identity decision separately from automatic account activation. Live funding still requires this LIVE identity decision plus the existing allowlist, clean-wallet and pilot limits.

## Customer evidence report

The portal also generates automatic account assessments, with customer and report IDs, email ownership proof, data-format checks, account and identity status, and reasons funding is unavailable. Filter pending, approved or all customers. Each page loads at most 50 customers with batched database reads. These are current assessments, not immutable identity decisions: correct-looking details do not establish identity ownership.

Email-verified customers can see payment controls and availability explanations while approval is pending. Their dashboard checks approval status every 30 seconds while visible, so a recorded approval can appear without signing out. The backend continues to enforce payment eligibility independently of the displayed controls.

Select an email-verified customer from the Active accounts list. Choose LIVE or TEST as the review environment. Review genuine identity evidence through a documented process outside this application, matching the evidence to the person. Enter a retained case reference and a report of 20 to 2000 characters describing evidence sources, checks, date, findings and concerns. Do not enter raw BVN, NIN, document numbers or identity images. The portal stores case references and findings, not documents.

For approval, confirm both completed email ownership verification and identity matching. Enter the reviewer's password, select the decision and confirm. The server independently requires the saved email-code record and prohibits self-review. Suspended, deleted and deactivated accounts cannot be reactivated by this flow. Approval records the immutable report and sets ACTIVE with REVIEWED identity status; it does not claim BVN_VERIFIED or FULL_KYC. A rejection of an active account suspends it. All reports retain customer ID, reviewer ID, evidence reference, identity method, environment, timestamp and decision.

BVN confirmation is optional for this documented manual approval route. Approval can be recorded while payments are OFF. Live funding still requires a LIVE approval, the explicit allowed customer UUID, a clean wallet/no test history, fees and existing restricted pilot limits. General live withdrawals, bills and group money remain unavailable. No administrator or genuine customer approval is created automatically by installing this portal.

## Independent optional BVN service

BVN controls remain absent from the customer interface pending provider approval. Future API identity onboarding uses separate FLW_IDENTITY_MODE (LIVE or TEST), FLW_IDENTITY_SECRET and FLW_IDENTITY_ENABLED. It no longer depends on SAVEX_MONEY_MODE or the payment secret. Changing these identity settings does not enable payments. When disabled, the identity status endpoint returns PAUSED without a consent URL.

Migrate and run with the persistent xampp profile. V15 adds manual report fields, REVIEWED status and the one-time setup lock. Existing audit records remain unchanged. The reviewer portal sends no payment or provider refund. Administrator tokens remain in memory and are cleared on sign-out.
