CREATE TABLE identity_checks (
 id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL UNIQUE,
 provider_reference VARCHAR(120) UNIQUE, consent_url VARCHAR(1000), status VARCHAR(32) NOT NULL,
 updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL,
 CONSTRAINT fk_identity_user FOREIGN KEY(user_id) REFERENCES users(id)
);
