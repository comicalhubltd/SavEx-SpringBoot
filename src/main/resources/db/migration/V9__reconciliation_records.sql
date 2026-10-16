CREATE TABLE reconciliation_issues (
 id CHAR(36) PRIMARY KEY, issue_key VARCHAR(160) NOT NULL UNIQUE,
 order_id CHAR(36), reason VARCHAR(60) NOT NULL, status VARCHAR(16) NOT NULL,
 created_at DATETIME(6) NOT NULL, resolved_at DATETIME(6), reviewer_id CHAR(36),
 evidence_reference VARCHAR(200), INDEX idx_reconciliation_status(status,created_at),
 CONSTRAINT fk_reconcile_order FOREIGN KEY(order_id) REFERENCES money_orders(id)
);
CREATE TABLE settlement_matches (
 id CHAR(36) PRIMARY KEY, order_id CHAR(36) NOT NULL UNIQUE,
 settlement_id VARCHAR(40) NOT NULL, gross_kobo BIGINT NOT NULL,
 provider_fee_kobo BIGINT NOT NULL, matched_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_settlement_order FOREIGN KEY(order_id) REFERENCES money_orders(id)
);
