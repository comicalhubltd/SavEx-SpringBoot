CREATE TABLE payout_wallets (
 id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, provider_mode VARCHAR(8) NOT NULL,
 state VARCHAR(24) NOT NULL, account_reference VARCHAR(80), barter_id VARCHAR(80),
 account_number VARCHAR(10), bank_code VARCHAR(6), bank_name VARCHAR(120), account_name VARCHAR(100),
 balance_kobo BIGINT NOT NULL DEFAULT 0, locked_kobo BIGINT NOT NULL DEFAULT 0,
 provider_available_kobo BIGINT, checked_at DATETIME(6), created_at DATETIME(6) NOT NULL,
 sync_requested BOOLEAN NOT NULL DEFAULT FALSE, sync_note VARCHAR(200), version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_psa_user FOREIGN KEY(user_id) REFERENCES users(id),
 UNIQUE(user_id,provider_mode), UNIQUE(account_reference), UNIQUE(provider_mode,account_number)
);
ALTER TABLE money_orders ADD debit_subaccount VARCHAR(80);
CREATE TABLE payout_funding_events (
 id CHAR(36) PRIMARY KEY, provider_mode VARCHAR(8) NOT NULL, transfer_id VARCHAR(20) NOT NULL,
 state VARCHAR(24) NOT NULL, created_at DATETIME(6) NOT NULL, next_check_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, UNIQUE(provider_mode,transfer_id)
);
CREATE INDEX idx_psa_funding_due ON payout_funding_events(provider_mode,state,next_check_at);
CREATE INDEX idx_psa_wallet_due ON payout_wallets(provider_mode,state,checked_at);
CREATE INDEX idx_psa_orders_source ON money_orders(debit_subaccount,status);
CREATE TABLE payout_wallet_recoveries (
 id CHAR(36) PRIMARY KEY, wallet_id CHAR(36) NOT NULL, reviewer_id CHAR(36) NOT NULL,
 account_reference VARCHAR(80) NOT NULL, created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_psa_recovery_wallet FOREIGN KEY(wallet_id) REFERENCES payout_wallets(id),
 CONSTRAINT fk_psa_recovery_reviewer FOREIGN KEY(reviewer_id) REFERENCES users(id)
);
CREATE TRIGGER psa_recovery_no_update BEFORE UPDATE ON payout_wallet_recoveries
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Payout wallet recovery evidence is immutable';
CREATE TRIGGER psa_recovery_no_delete BEFORE DELETE ON payout_wallet_recoveries
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Payout wallet recovery evidence is immutable';
