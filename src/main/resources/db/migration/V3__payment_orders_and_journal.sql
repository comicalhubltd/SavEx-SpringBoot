CREATE TABLE money_orders (
 id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, idempotency_key VARCHAR(80) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL, business_key VARCHAR(120), kind VARCHAR(24) NOT NULL,
 status VARCHAR(24) NOT NULL, currency VARCHAR(3) NOT NULL, amount_kobo BIGINT NOT NULL,
 reference VARCHAR(80) NOT NULL UNIQUE, destination VARCHAR(120), service_id VARCHAR(40),
 variation_code VARCHAR(120), provider_reference VARCHAR(120), checkout_url VARCHAR(600),
 group_id CHAR(36), cycle_number INT, next_check_at DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL,
 CONSTRAINT uq_money_idempotency UNIQUE(user_id,idempotency_key),
 CONSTRAINT uq_money_business UNIQUE(business_key),
 CONSTRAINT fk_money_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT ck_money_amount CHECK(amount_kobo > 0),
 CONSTRAINT ck_money_currency CHECK(currency = 'NGN'),
 INDEX idx_money_due(status,next_check_at),
 INDEX idx_money_history(user_id,created_at)
);
CREATE TABLE journal_postings (
 id CHAR(36) PRIMARY KEY, posting_key VARCHAR(120) NOT NULL UNIQUE,
 debit_account VARCHAR(120) NOT NULL, credit_account VARCHAR(120) NOT NULL,
 amount_kobo BIGINT NOT NULL, currency VARCHAR(3) NOT NULL, created_at DATETIME(6) NOT NULL,
 CONSTRAINT ck_posting_amount CHECK(amount_kobo > 0),
 CONSTRAINT ck_posting_accounts CHECK(debit_account <> credit_account),
 CONSTRAINT ck_posting_currency CHECK(currency = 'NGN')
);
