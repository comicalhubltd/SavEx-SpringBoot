# Automatic transaction matching and tabular reports — 12 October 2026

## Using the update

Restart SavEx through its existing local startup workflow and reload the browser. Sign in to `/admin.html` and open **Flutterwave transaction matching**. The application automatically imports recent history every five minutes while the payment worker is enabled and the application is running. The first scheduled check is dispatched about 15 seconds after startup. **Sync Flutterwave transactions** requests an asynchronous check; **Refresh report** reloads the latest comparison. The page refreshes this report every ten seconds while visible. Successful, failed, matched and attention filters and 50-row pagination are provided.

Existing web report views are now responsive tables: customer account summaries, selected customer details/checks/blockers, recorded identity decisions, merchant balance, wallet-to-journal comparisons, open exceptions, Flutterwave comparisons, customer payment history and printable receipts. Wide tables scroll horizontally and support keyboard focus. Financial actions and receipt/resume buttons remain available in their table cells.

## What the sync does

- Calls the authenticated Flutterwave v3 transaction-list API using a date window and pagination. The default window is the last 30 calendar dates including today. It queries unfiltered, successful and failed history separately because provider defaults can omit failed attempts.
- Imports only SavEx-prefixed references. Unrelated merchant business transactions and raw provider customer/email/account payloads are not retained in the report.
- De-duplicates repeated transaction IDs across queries and pages. Distinct IDs under one reference remain distinct attempts. Conflicting details for an ID are explicitly flagged.
- Looks up local orders in batches and checks reference, environment, service type, saved provider ID, expected funding total (principal plus saved SavEx fee), currency and status.
- Reports matched successes/failures/reversals, other failed attempts, discrepancies, provider-only records, pending outcomes, missing saved attempts and possible duplicate successes.
- Places critical and attention findings first. Multiple successful attempts sharing a reference are not combined into a single apparent successful order.
- Uses a dedicated single background thread and bounded date/page/row limits so the scheduler can continue dispatching payment-recovery work. Manual checks are throttled to at most one start per minute per application process.

The comparison is **report-only**. It never credits/debits wallets, changes local payment statuses, creates settlement evidence, clears exceptions or restores restricted accounts. Existing payment-verification and reconciliation controls remain in place. A history-list match is not independent bank receipt/settlement evidence and must not replace the existing transaction-by-ID verification before giving financial value.

## Reading the results

| Result | Meaning |
|---|---|
| MATCHED SUCCESS | Saved attempt ID, reference, total, currency and successful status agree |
| MATCHED FAILURE | The saved failed attempt's ID/details/status agree |
| OTHER FAILED ATTEMPT | Another failed ID shares the reference; it does not invalidate a different successful attempt |
| NEEDS RECONCILIATION / ATTEMPT REQUIRES REVIEW | Provider success or identity differs from the saved successful attempt |
| POSSIBLE DUPLICATE SUCCESS | Multiple successful IDs share a reference |
| DETAILS MISMATCH / CRITICAL DISCREPANCY | Amount/currency or the saved attempt's final status conflicts |
| NOT FOUND / SAVED ATTEMPT NOT FOUND | No matching observation in the imported window/pages; not proof of failure |
| NOT FOUND IN PARTIAL IMPORT | Missing while provider access, paging or limits prevented full import |
| INVALID PROVIDER DATA | Missing, invalid or conflicting provider data |

The sync status is separate from payment status. **FAILED** at the top means the import failed, not that a customer payment failed. **PARTIAL** means available observations are shown but one or more history queries/pages could not be completed. Individual matched rows can appear in a partial report; missing records remain uncertain. Counts refer to attempt/missing-record rows, not unique customer deposits or funds totals.

## Live validation and external limitation

Read-only live provider checks on 12 October 2026 used the 13 September–12 October range. The successful-history request returned success, one page and four SavEx-related records. The failed-history request returned **HTTP 403**. The software continues importing available successful/unfiltered results and identifies failed-filter coverage as incomplete; it does not hide this access failure or invent failed outcomes. Provider permission, endpoint/edge security restrictions and failed-history availability need investigation with Flutterwave. Neither balances nor transactions were changed during these checks.

## Configuration and retention

| Optional variable | Default | Purpose |
|---|---|---|
| SAVEX_HISTORY_SYNC_ENABLED | true | Enable scheduled checks (manual checks remain available to admins) |
| SAVEX_HISTORY_SYNC_DELAY_MS | 300000 | Scheduled dispatch interval; active checks are never overlapped in one process |
| SAVEX_HISTORY_DAYS | 30 | Rolling date window, 1–366 days |
| SAVEX_HISTORY_MAX_PAGES | 50 | Maximum pages per status query, 1–1000 |
| SAVEX_HISTORY_MAX_ROWS | 5000 | Unique provider-attempt cap and local-window order cap, 1–50000 |

OFF mode performs no provider reads. Scheduled checks also respect the existing worker-enabled switch. Reports are scoped to the current payment environment and hide snapshots from another environment. Limits and failures produce partial reports rather than claims of full reconciliation. Increase the window if historical records are older than the default range; inspect partial-limit messages before relying on missing-record results.

The latest comparison is held in application memory and is rebuilt after restart. No schema migration or persistent provider-payload storage is introduced. It is an operational view, not a new immutable financial audit snapshot. Multiple application instances each have their own timer/cache; a shared scheduler/snapshot store is future infrastructure work. Existing immutable accounting/review records remain the financial evidence source.

## APIs and source

- Admin-only `POST /api/v1/admin/reconciliation/transactions/sync` starts a check without waiting for all provider pages.
- Admin-only `GET /api/v1/admin/reconciliation/transactions?page=0&filter=ALL` reads the current report; filters: ALL, ATTENTION, MATCHED, SUCCESS, FAILED.
- Matching and scheduling: `src/main/java/com/savex/money/TransactionComparison.java`, `TransactionHistorySync.java`, `FlutterwaveGateway.java`, `ReconciliationController.java`.
- Tables: `src/main/resources/static/report-table.js`, `admin.js`, `app.js`, shared stylesheet. Static cache advanced to v17.
- Provider contract: [Flutterwave transaction-list API](https://developer.flutterwave.com/reference/get-all-transactions).

## Validation

Maven verify succeeded: **79 tests total, 77 passed, 2 skipped**, zero failures/errors. All **18 JavaScript tests passed**, including semantic table rendering, text-only handling of untrusted values, empty states, receipt behavior and existing checkout/session safeguards. Modified JavaScript files pass syntax checks.

New backend fixtures cover exact successful/failed matches including the funding fee, separate attempts under a shared reference, multiple successes, status/amount/currency/environment differences, missing IDs, conflicting provider observations, pagination limits, provider failure redaction and retaining available successes when failed history is forbidden. Customer accounts cannot access either administrator sync/report endpoint. Report code only reads local order repositories; no financial repository write is called.

Logs: `history-sync-tests.log`; isolated backend reports/build: `.build/history-sync/`. Live provider validation used read-only GET requests. No credentials, live balances, restrictions or database schema were changed. The live application was not restarted and the updated pages were not acceptance-tested in a running browser during this change. Full settlement/bank matching, recovery actions, durable report snapshots and older history beyond the configured window remain separate work.

