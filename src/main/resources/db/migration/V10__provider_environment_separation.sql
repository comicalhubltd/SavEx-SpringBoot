ALTER TABLE money_orders ADD provider_mode VARCHAR(8) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE identity_checks ADD provider_mode VARCHAR(8) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE customer_reviews ADD provider_mode VARCHAR(8) NOT NULL DEFAULT 'UNKNOWN';
