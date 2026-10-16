# SavEx: project review and revenue roadmap

Prepared for an international ambition with Nigeria as the first operating market. Individuals, small businesses, and savings groups are all potential customers, but the first commercial offer should be narrow enough to validate.

## 1. What the project is

SavEx models a rotating savings circle: members agree a contribution amount, frequency, group size, and payout order. The original project contains a Spring Boot API, MySQL migrations, and an incomplete Flutter prototype. Its strongest existing domain is group savings.

The updated delivery model is a Java-first modular application with one installable browser client. Java owns business rules, authentication, persistence, and API contracts; the browser needs HTML, CSS, and JavaScript for responsive layout and device installation. Java alone cannot supply browser service-worker behaviour.

### Capability inventory

| Area | Implemented / available | Still required |
| --- | --- | --- |
| Web and mobile | Responsive PWA, manifest, icons, install prompt, offline public shell | Real-device acceptance across supported browsers |
| Accounts | Registration, BCrypt, JWT access tokens, normalized email/phone login | Production email/phone verification, recovery, MFA, refresh rotation/revocation, rate limiting |
| Local development | Disposable H2 database; local accounts active; VS Code run/test/debug | Persistent MySQL integration acceptance and deployment automation |
| Wallet | Read balances in NGN; integer kobo persistence | Double-entry ledger, reconciliation, holds, statement history |
| Savings circles | Create, list own circles, join by code, membership-restricted details, capacity check under a write lock | Contributions, scheduled collection, late/default handling, payout execution, group privacy controls |
| Payments | Inherited Paystack initialization/webhook code, disabled by default | Signed webhook validation, provider verification, idempotency, amount/currency checks, disputes and retries |
| Notifications | Mail/WebSocket dependencies and notification schema | Actual delivery workflows, preferences and reliable retry |
| International | Product roadmap | Country eligibility, currencies, providers, local controls and approvals |

The old public group directory still exposes basic group metadata; invite codes and creator names are now excluded. Add explicit public/private visibility before allowing sensitive circles on a public service.

## 2. Architecture and code map

| Location | Responsibility |
| --- | --- |
| `controller/` | REST requests and validation entry points |
| `service/` | Registration, savings-group operations, payment orchestration |
| `repository/` | Spring Data JPA database operations |
| `entity/` | Users, wallets, groups, membership, cycles, transactions, locations |
| `dto/` | Request/response contracts; avoid returning persistence entities |
| `security/` | JWT verification and account lookup |
| `config/` | Route security and API cache policy |
| `resources/db/migration/` | Versioned MySQL schema and Nigerian reference data |
| `resources/static/` | Responsive web/PWA client, manifest and service worker |
| `application-dev.yml` | Loopback-only, disposable local environment |
| `application-prod.yml` | External secrets and persistent database configuration |

Keep one deployable **modular monolith** initially. As the team grows, organise by business capability: identity, circles, ledger, payments, billing, reporting and notifications. Give each capability an explicit Java service boundary. Separate payment-provider adapters from ledger posting; emit outbox events only after the database transaction commits. Microservices can wait until scale or team ownership provides a measurable reason.

Use integer minor units plus an ISO currency code in the future ledger. Current code is NGN-specific, with some decimal response fields; it is not already multi-currency. Do not treat all currencies as having two decimal places. Enforce overflow limits and use explicit rounding for pricing and conversion.

## 3. Commercial direction

Recommended first offer: **paid savings-circle administration for organisers and small businesses**. This builds on the existing group model and gives a clear buyer: the person currently maintaining spreadsheets and chasing contributions. Validate demand before building broad consumer banking.

| Segment | Product to test | Initial price hypothesis (not market evidence) | Metric |
| --- | --- | --- | --- |
| Individuals | Free core savings planning; premium goals, reminders and statements | ₦1,000–₦2,000/month for premium | Paid conversion and 90-day retention |
| Savings groups / cooperatives | Member records, reminders, exports, roles, dual approvals, audit history | ₦5,000–₦15,000/group/month | Active paid groups and organiser time saved |
| Small businesses | Cash-reserve goals, collections tracking, invoice matching, accountant access | ₦10,000–₦30,000/business/month | Revenue per business and reconciliation effort saved |
| Institutions, later | Branded portal and reporting/API access | Negotiated setup and annual subscription | Sales cycle, onboarding cost and gross margin |

Interview organisers in trader associations, employer communities and existing cooperatives. Ask them to show their current process, quantify missed contributions and time spent, and trial a paid administrative product. Do not infer willingness to pay from sign-ups alone.

Possible later revenue: contracted payment-service margin, disclosed referrals to appropriately licensed partners, and enterprise reporting. Avoid relying on deposit float, lending customer funds, speculative investments, or promising returns. Each changes the risk and potentially the regulatory model.

### A practical fee problem in the existing code

The inherited code adds a “platform fee” of 1.5%, capped at ₦2,000. It does not establish profit after payment-provider charges. Paystack currently publishes Nigerian local processing at 1.5% + ₦100, with the ₦100 waived below ₦2,500 and a ₦2,000 cap. Confirm the exact merchant contract and channel before implementation. [Paystack pricing](https://support.paystack.com/en/articles/2130306)

Illustration using that published rate: for a ₦10,000 wallet credit, the existing code charges ₦10,150. Processing at 1.5% + ₦100 costs about ₦252.25, leaving ₦9,897.75 before other costs. Crediting ₦10,000 creates a ₦102.25 shortfall. This is an arithmetic example, not a settlement quote; taxes, contract terms, rounding and other charges may change it.

Build a Java pricing service that separates wallet principal, provider fee, SavEx fee, taxes and total charged. Show a fee quote before confirmation, persist the accepted quote, and reconcile actual provider charges. Customer deposits are liabilities, not revenue.

### Illustrative monthly economics

These are planning assumptions, not forecasts or validated demand:

- 1,000 premium individuals × ₦1,500 = ₦1,500,000.
- 100 paid circles × ₦10,000 = ₦1,000,000.
- 50 small businesses × ₦20,000 = ₦1,000,000.
- Total subscription revenue = ₦3,500,000/month.
- At an assumed ₦600,000 variable delivery cost, contribution is ₦2,900,000.
- At a further assumed ₦2,000,000 fixed operating cost, operating surplus is ₦900,000 before tax, capital costs and exceptional losses.

Avoid counting the same paying account twice. Build conservative, base and optimistic scenarios from observed conversion, churn and support costs. Track gross margin, customer acquisition cost, payback period, failed collections, fraud losses and reconciliation exceptions. Acquisition spend should follow demonstrated retention and positive contribution economics.

## 4. Nigeria-first, international-ready

Start with NGN accounts and Nigerian onboarding. The current phone validator is deliberately Nigerian; displaying the site abroad does not make it an internationally operational financial service.

For each new country, require a separate launch decision covering eligible customers, licensed partners, settlement currencies, fees, tax handling, identity checks, disputes, privacy and support. Use country/currency configuration and provider adapters, not scattered country checks. Add international phone parsing and address formats only alongside actual supported-country eligibility.

Nigeria's CBN publishes payment-service-provider categories and licensing information. The appropriate arrangement depends on whether SavEx merely supplies software or holds/transfers funds and performs other regulated activities; a provider integration alone does not answer that question. Obtain a product-specific assessment and partner contract before real-money operations. [CBN payment-service providers](https://www.cbn.gov.ng/PaymentsSystem/PSPs.html)

Build a data inventory, retention schedule, access controls, consent/notice flows where applicable, incident response and handling for customer data requests around Nigeria's data-protection framework. Minimise stored identity data, including BVN; use protected provider references where possible. [Nigeria Data Protection Act](https://ndpc.gov.ng/download/nigeria-data-protection-act-2023)

Investment products and investment-based crowdfunding are separate expansion decisions; review the relevant SEC framework rather than assuming savings-circle functionality permits them. [SEC rules for FinTechs](https://www.sec.gov.ng/our-mandate/regulation/rules-and-regulations/sec-rules-for-fintechs/)

## 5. Delivery sequence and acceptance gates

**First: usable planning pilot.** Run the PWA with invited testers, validate sign-up, create/join flows and mobile accessibility, and interview prospective paid organisers. Use disposable/synthetic data in the development profile. Choose a group-privacy policy and add production verification before an externally hosted account pilot.

**Next: trusted money foundation.** Implement double-entry postings and balance invariants; verify raw-body webhook signatures; compare provider amount, currency, reference and status; lock or atomically transition transactions; make duplicate events no-ops; fail and roll back when crediting fails; add reconciliation, refunds and dispute states. Paystack specifies HMAC SHA512 webhook signatures and warns against duplicate fulfilment. [Webhooks](https://paystack.com/docs/payments/webhooks/), [payment verification](https://paystack.com/docs/payments/verify-payments/)

The current webhook receives a signature but does not validate it; it is also behind bearer authentication, so it is not a working production callback. Its pending-status check alone is insufficient protection against concurrent duplicates. Keep payments disabled until these paths are replaced and tested.

**Then: paid organiser pilot.** Add schedules, consented reminders, statements/exports, role-based organiser tools, billing entitlements and transparent fees. Require supervised payout approval and a documented recovery process. A full circle currently changes to ACTIVE, but no collection or payout engine is implemented.

**Finally: measured expansion.** Add SMB invoicing/reconciliation, institutional plans and new-country adapters after unit economics and operational controls are demonstrated.

Before a real-money launch, test MySQL migrations from both an empty database and a representative existing version; backup restore; load and concurrency; account takeover protections; audit records; sensitive-data logging; provider sandbox flows; fee/ledger reconciliation; and an independent security review. Add MFA/step-up authentication for financial actions, account recovery, rate limits and fraud controls. Infrastructure dependencies in the project do not implement these controls automatically.

## 6. Changes made in this conversion

- Added a responsive, installable Spring-served web/mobile interface.
- Added memory-only browser authentication, safe text rendering, API no-store headers and shell-only offline caching.
- Added VS Code recommendations, tasks and debugger setup, plus Java CI.
- Added disposable local development and explicit external-secret deployment profiles.
- Updated Java build compatibility while keeping Java 21 output.
- Fixed the MySQL JSON mapping and quoted reserved group table name.
- Restricted API access to access tokens and enabled accounts; normalized login/registration identities.
- Restricted group detail access, removed public invite-code disclosure and locked join operations.
- Disabled inherited payments; documented the unfinished flows rather than presenting them as ready.
- Corrected the Docker build and non-root Java runtime.

See the root README for run instructions and the validation note for what was actually executed. Hosted deployment, real-money provider testing and international enablement are outside this conversion.
