# SavEx — web and mobile savings circles

The separate [reviewer portal](docs/REVIEWER_PORTAL.md) now supports email-verified administrator setup and documented customer approval without BVN confirmation. Optional BVN API access has its own environment/key, independent of payment mode.

Email-only contact verification now supports expiring Gmail codes and persistent resend/attempt limits. See [email verification setup](docs/EMAIL_VERIFICATION.md). BVN evidence and independent identity approval remain required.

Latest payment work: see [restricted live pilot and fee controls](docs/RESTRICTED_LIVE_PILOT.md). The backend now supports an explicitly configured collections-only pilot after independent customer review; general real-money services remain unavailable. Historical release statements below describe the earlier integration.

SavEx is a Java-first savings-circle application. The same Spring Boot server delivers a responsive web interface and an installable Progressive Web App (PWA) for phones, tablets, and desktops. No Node.js or Flutter toolchain is required for this interface.

**Current stage: Flutterwave test integration; real-money launch is blocked pending validation.** The project now includes hosted wallet funding, bank transfer and airtime/data adapters, an idempotent payment ledger, group contributions and fully funded cycle payouts, and BVN consent/evidence retrieval. Payments default to OFF, and LIVE mode cannot start. NIN verification and production identity, authorisation, reconciliation and approval work remain. See [Flutterwave setup, release status and revenue plan](docs/FLUTTERWAVE_RELEASE.md).

## Run locally

To connect the existing Flutterwave services, follow [Payment connection](docs/PAYMENT_CONNECTION.md). The local launcher loads payment credentials from `.env`; the wallet funding endpoint shares the payment ledger and requires an idempotency key.

Prerequisites: JDK 21 or 25 and Maven 3.9+. Open this project folder in VS Code and accept its recommended Java and Spring extensions.

1. Run `mvn spring-boot:run` in the VS Code terminal, or use **Run and Debug → SavEx: local development**.
2. Open [SavEx](http://localhost:8080).
3. Create an account with a Nigerian-format phone number and a password of at least eight characters.
4. Create a savings circle. Open a second browser session, register another account, and join using the invite code.

The default `dev` profile uses an **in-memory H2 database**; data disappears at shutdown. It binds to `127.0.0.1`, disables payments, and activates local test accounts automatically without marking email, phone, or KYC as verified. The development signing key is public and must never be used on a hosted service. There is no seeded password or sample balance.

VS Code tasks include **SavEx: run**, **SavEx: test**, **SavEx: package**, and **SavEx: start databases**. A JDK must be installed, not just a JRE. If VS Code selects a different JDK, use its **Java: Configure Java Runtime** command. Maven and the Java debugger do not automatically import Docker's `.env` file.

## Build and test

```text
mvn test
mvn clean package
java -jar target/savex-backend-1.0.0-SNAPSHOT.jar
```

Tests exercise Spring routing, authentication, group access controls, token separation, suspended accounts, payment idempotency, held funds on provider uncertainty, exact amount/currency/reference checks, concurrent spending, and fully funded group payouts. Browser transport tests run with `node --test scripts/api-client.test.cjs`. H2 tests do not replace a MySQL migration/upgrade test.

## Install on a device

The manifest and service worker are served by Spring Boot. Chrome/Edge may display **Install app** when browser requirements are met. On iPhone/iPad, use Safari → Share → Add to Home Screen.

Installation and service workers require HTTPS except on localhost. A phone cannot reach your computer using the phone's own `localhost`. For device testing, use a private HTTPS development endpoint with access controls; do not expose the default development profile publicly. Browser support and install prompts vary.

Only the public app shell is cached. Authentication, wallet balances, group data, and money requests are never cached by the service worker. Offline users can see the shell, but all account operations require a connection. Tokens exist only in memory: refresh/reopen the page to sign in again. This intentionally avoids storing bearer tokens in browser storage.

## Persistent database / deployment preparation

The existing MySQL 8/Flyway and Redis setup remains in `docker-compose.yml`. Copy `.env.example` to `.env` and supply secrets, then use `docker compose up --build` for a local container integration environment. Compose explicitly selects `prod`; this is a profile name, not a claim of production readiness. Its database passwords and plaintext internal database connection are development defaults and need replacement before hosting.

For an external deployment, set `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USER`, `DB_PASS`, and a Base64 `JWT_SECRET` containing at least 32 cryptographically random bytes. Configure TLS, managed database security, backups, and restricted infrastructure access. Swagger is disabled under `prod`. New production accounts remain pending verification because the original verification workflow is not implemented.

**Do not enable payments merely by changing a configuration flag.** The inherited payment integration has unresolved signature verification, replay/concurrency, settlement reconciliation, and failure-recovery gaps. See the launch gates in [the project and revenue roadmap](docs/PROJECT_AND_REVENUE.md).

## Architecture

- Java 21 target, Spring Boot 3.5, Spring MVC and Spring Security.
- Controllers → services → Spring Data JPA repositories → MySQL/Flyway.
- H2 for disposable local development.
- JWT bearer authentication, BCrypt password hashing.
- HTML/CSS and a small JavaScript client served from `src/main/resources/static`.
- Existing Flutter files in `mobile/` are preserved as a legacy prototype, not required for the PWA.
- Existing Redis, mail, WebSocket and payment dependencies are foundations; dependency presence does not mean complete product features.

See [project details and FinTech revenue roadmap](docs/PROJECT_AND_REVENUE.md) for existing capabilities, gaps, monetisation experiments, Nigeria-first expansion, and delivery priorities.

## If the page opens but registration says the server cannot be reached

The installable app caches its public screen. That screen can open even when Java is stopped; creating an account still needs the running server. In VS Code, run `mvn spring-boot:run`, leave that terminal running, wait for the application startup message, and reload http://localhost:8080. Internet access alone does not start the local server.

If registration times out, try signing in with the same details before submitting another registration: the server might already have created the account. The frontend does not automatically retry account creation. Local development accounts disappear when the in-memory server is restarted.

Client connection regression checks (optional Node.js 18+): `node --test scripts/api-client.test.cjs`.
