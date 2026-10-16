# Validation record — 24 September 2026

Environment: Windows, Maven 3.9.14, Eclipse Temurin JDK 25.0.2; Java compilation target 21.

## Passed

- Main Java source compilation with Spring Boot 3.5.16.
- Registration, normalized email/phone login, duplicate registration rejection, zero wallet balance and API no-store policy.
- Access-token versus refresh-token separation.
- Circle creation, own-circle listing, membership-restricted details, public invite-code redaction, joining and capacity rejection.
- Invalid contribution rejection, suspended-account rejection and disabled payment initiation.
- Homepage forwarding, index content, PWA manifest, JavaScript/CSS/service-worker resources and PNG icons.
- JavaScript syntax checks for app.js and sw.js.

The first full run executed four tests: three passed, and the shell test failed because MockMvc reports Spring's index forwarding rather than rendering its body. The assertion was corrected to check the forward and request /index.html separately. The targeted rerun passed (1 test, 0 failures, 0 errors). The other three passing tests were not unnecessarily repeated.

Logs: target/build-check.log and target/shell-check.log (generated locally, ignored by Git).

## Not verified here

- Browser visual inspection and actual PWA installation: browser automation timed out twice.
- Real Android/iPhone/tablet devices, accessibility assistive technology and browser-specific offline behaviour.
- Docker image execution, MySQL/Flyway upgrade compatibility and Redis integration; Docker was not available on PATH.
- Java 21 runtime execution: local checks used Java 25; the added CI matrix covers both versions when run.
- Real provider payments, webhook correctness, withdrawals, payout scheduling, KYC and international operations. These are not completed features.

Before external release, follow the launch gates in PROJECT_AND_REVENUE.md. The local test environment is not a benchmark: command and JVM startup were unusually slow.

Executable JAR packaging: passed with mvn -DskipTests package. Tests were skipped during packaging because the checks above were already executed.
