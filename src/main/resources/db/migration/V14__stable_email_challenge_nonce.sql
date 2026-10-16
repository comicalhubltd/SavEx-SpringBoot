ALTER TABLE email_challenges ADD COLUMN nonce VARCHAR(36) NULL;
-- Earlier hashes depended on timestamps whose precision can change over JDBC.
-- Retain quotas and verified evidence; require a fresh code for outstanding challenges.
UPDATE email_challenges SET code_hash = NULL WHERE verified_at IS NULL;
