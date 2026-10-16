CREATE TABLE customer_reviews (
 id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, reviewer_id CHAR(36) NOT NULL,
 decision VARCHAR(16) NOT NULL, evidence_reference VARCHAR(200) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_review_customer FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id),
 INDEX idx_review_customer(user_id,created_at)
);
ALTER TABLE money_orders ADD application_fee_kobo BIGINT NOT NULL DEFAULT 0;
ALTER TABLE money_orders ADD provider_amount_kobo BIGINT NOT NULL DEFAULT 0;
UPDATE money_orders SET provider_amount_kobo=amount_kobo;
ALTER TABLE money_orders ADD provider_fee_kobo BIGINT NOT NULL DEFAULT 0;
ALTER TABLE money_orders ADD CONSTRAINT ck_application_fee CHECK(application_fee_kobo >= 0 AND application_fee_kobo <= 50000);
