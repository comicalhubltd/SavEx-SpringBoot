CREATE TABLE ai_records (
 id CHAR(36) NOT NULL PRIMARY KEY,
 kind VARCHAR(16) NOT NULL,
 state VARCHAR(32) NOT NULL,
 record_key VARCHAR(120) NULL UNIQUE,
 actor_id CHAR(36) NULL,
 parent_id CHAR(36) NULL,
 created_at TIMESTAMP(6) NOT NULL,
 reviewed_by CHAR(36) NULL,
 reviewed_at TIMESTAMP(6) NULL,
 payload TEXT NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 INDEX idx_ai_kind_created (kind,created_at)
);
