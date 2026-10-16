CREATE TABLE bank_settlement_receipts (
 id CHAR(36) PRIMARY KEY, order_id CHAR(36) NOT NULL UNIQUE,
 reviewer_id CHAR(36) NOT NULL, net_kobo BIGINT NOT NULL,
 evidence_reference VARCHAR(200) NOT NULL, created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_receipt_order FOREIGN KEY(order_id) REFERENCES money_orders(id),
 CONSTRAINT fk_receipt_reviewer FOREIGN KEY(reviewer_id) REFERENCES users(id)
);
CREATE TRIGGER bank_receipt_no_update BEFORE UPDATE ON bank_settlement_receipts
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Bank evidence is immutable';
CREATE TRIGGER bank_receipt_no_delete BEFORE DELETE ON bank_settlement_receipts
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Bank evidence is immutable';
