# AI payment investigation and daily improvement

## Enable and use

The configured OpenAI key passed a read-only model-access check for `gpt-4.1-mini`. No transaction data was used in that check and no AI generation was performed during implementation.

Set these private environment values, then restart SavEx using the normal persistent `xampp` workflow and reload the browser:

```env
OPENAI_API_KEY=your_private_key
SAVEX_AI_ENABLED=true
SAVEX_AI_MODEL=gpt-4.1-mini
SAVEX_AI_DAILY_LIMIT=50
SAVEX_AI_LEARNING_ENABLED=true
```

The key is never put in browser code or returned by the API. AI generation defaults to disabled; the implementation does not automatically enable it in `.env`. A valid key does not establish billing credit or guarantee response generation. API usage is billed separately from the desktop chat subscription.

Restart applies migration `V17__ai_investigation_records.sql`, adding the AI report, feedback, lesson and budget table. Existing financial tables and balances are not modified by the migration. Use the normal database backup procedure before deployment. No production migration or application restart was performed during implementation.

Open **Administrator portal → AI investigations → Refresh AI reports**. Enter an existing SavEx transaction reference and select **Investigate with AI**. The agent produces a structured explanation with uncertainties and suggested next steps. Recorded status, settlement and bank-receipt flags appear separately from generated advice. Every model result is marked unverified.

## Evidence and capabilities

The server gathers local payment facts, that customer's wallet-to-journal comparison, recorded collection settlement and bank-receipt flags, and cached transaction-history comparisons. It explicitly states freshness and coverage limitations. Collection settlement evidence is distinguished from withdrawal evidence. The agent does not fetch a bank statement or make a fresh Flutterwave query. Missing or partial evidence is not proof of failure. It does not solve IP restrictions or replace provider support.

Customer names, email, phone, bank account numbers, customer UUIDs, provider transaction IDs, original transaction references and credentials are not sent to the model. Model input contains redacted accounting facts, timestamps, statuses and approved guidance. Original references are retained only in the local administrator investigation report. The UI discloses that redacted facts are sent to OpenAI when enabled.

The Responses API request uses structured JSON output, a 1,500-output-token cap, `store=false`, no tools and a 20-second timeout. `store=false` is not a claim that the provider retains no operational data; provider data handling is governed by the account's applicable terms and settings. Failures, refusal, truncated responses and malformed outputs do not trigger financial actions or automatic model retries. Raw provider error bodies are not exposed.

The agent cannot submit or retry payments, refund, release reserved funds, approve identity, reactivate an account, edit balances, execute SQL, or send messages externally. It recommends human checks; the existing deterministic financial controls remain authoritative. Its output can still be mistaken and requires administrator review.

## Daily learning

1. Administrators rate an investigation Helpful, Status explanation inaccurate, Missing evidence not explained, or Next steps unclear. Each administrator can submit one rating per investigation; duplicate feedback is rejected.
2. While SavEx is running, an hourly local task creates yesterday's feedback review using Africa/Lagos dates. Restart catches up the previous day on the first tick after one minute. It does not backfill every missed historical day. **Review yesterday's feedback** also generates that review on demand.
3. The review counts feedback and proposes bounded guidance for the reported weakness. Guidance is selected from safe application-owned templates, not arbitrary feedback text or customer instructions. No external AI call is needed for this step.
4. Lessons begin Pending. An administrator must explicitly approve a lesson before it is reused in later model requests. Reject or revoke removes it from future requests. Past investigation evidence and used lesson IDs are retained; approval and revocation decisions are separately recorded.

This is administrator-reviewed memory and feedback adaptation, not autonomous model retraining or guaranteed daily accuracy improvement. Days without feedback generate an empty review rather than inventing lessons. At most 1,000 feedback records are included per day; reports flag incomplete aggregation. The most recent 10 approved lessons are reused, and the UI shows the latest 100 reports per type. A formal benchmark/evaluation programme is separate future work.

## Limits and audit

One generation is allowed at a time per application instance. A database-backed Africa/Lagos daily counter limits requests across restarts and instances. Model failures consume the attempt allowance. The first budget-row creation may race across instances; a conflicting request fails without calling the model and can be retried manually. Set provider-side spend controls separately; a request count is not a currency budget.

Reports, ratings, daily summaries, guidance states and reviewer decisions persist in MySQL. They do not automatically expire; retention and archival policies should be agreed for production. Disabling AI generation leaves reports and deterministic investigation views available. `SAVEX_AI_LEARNING_ENABLED=false` stops the automatic daily job; administrators can still request a daily review manually.

## Validation and limits

Tests exercise model response parsing and refusal/incomplete handling, no tools or key in request bodies, evidence redaction, administrator-only access, daily learning and review/revocation, duplicate feedback, durable attempt limits and unchanged financial records. Model outputs in tests are fixtures; no billable live generation or running-browser acceptance was performed.

Official integration references: [Structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs) and [configured model](https://developers.openai.com/api/docs/models/gpt-4.1-mini).
