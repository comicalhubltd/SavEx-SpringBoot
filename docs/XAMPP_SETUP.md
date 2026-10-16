# XAMPP persistent setup status

XAMPP contains MariaDB 10.4.32. The xampp profile uses its Hibernate dialect, Flyway migrations and schema validation. It does not delete data on shutdown. Migrations must still be validated against this database.

Database connectivity was restored on 6 October 2026. The savex database and dedicated savex_app account were created. Seven Flyway migrations applied successfully and schema validation passed. A persistent instance started on port 8081 and its health endpoint reported UP. The existing port 8080 instance still uses temporary development storage. No restart recovery exercise or live provider payment has been performed.

After the server accepts connections, create a new savex database with utf8mb4 and a dedicated savex_app account restricted to it. Enter its password in DB_PASS in .env, together with DB_URL and DB_USER from .env.example. Set JWT_SECRET to a private Base64-encoded random value containing at least 32 bytes.

Start with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -Profile xampp
```

Validate migrations and persistence in OFF/TEST mode. This profile retains pending account verification, rather than automatically activating development users. H2 accounts and balances are not copied.

LIVE remains blocked. The current Flutterwave credential is a test key. Customer verification, late-outcome and settlement reconciliation, database recovery and merchant access validation remain required. No real payment was sent.

