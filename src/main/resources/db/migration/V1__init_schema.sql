-- SavEx Database Schema — V1 (Flyway managed)
-- Targets MySQL 8.0+
-- UUIDs stored as CHAR(36) — readable and compatible with Hibernate 6 UUID mapping
-- Soft-delete pattern: deleted_at IS NULL = active record

-- ─────────────────────────────────────────────
-- LOOKUP / REFERENCE TABLES
-- ─────────────────────────────────────────────

CREATE TABLE states (
    id          SMALLINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        CHAR(3)         NOT NULL UNIQUE,
    name        VARCHAR(50)     NOT NULL UNIQUE,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE lgas (
    id          INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    state_id    SMALLINT        NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_lga_state_name (state_id, name),
    CONSTRAINT fk_lga_state FOREIGN KEY (state_id) REFERENCES states(id)
);

-- ─────────────────────────────────────────────
-- USERS & AUTH
-- ─────────────────────────────────────────────

CREATE TABLE users (
    id              CHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(150) NOT NULL UNIQUE,
    phone           VARCHAR(20)  NOT NULL UNIQUE,
    password_hash   VARCHAR(72)  NOT NULL,
    bvn             VARCHAR(11),
    profile_photo   TEXT,
    state_id        SMALLINT,
    lga_id          INT,
    address         TEXT,
    role            ENUM('MEMBER', 'ADMIN')                                          NOT NULL DEFAULT 'MEMBER',
    status          ENUM('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED') NOT NULL DEFAULT 'PENDING_VERIFICATION',
    kyc_level       ENUM('UNVERIFIED', 'BVN_VERIFIED', 'FULL_KYC')                  NOT NULL DEFAULT 'UNVERIFIED',
    email_verified  TINYINT(1)  NOT NULL DEFAULT 0,
    phone_verified  TINYINT(1)  NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at      DATETIME(6),
    CONSTRAINT fk_user_state FOREIGN KEY (state_id) REFERENCES states(id),
    CONSTRAINT fk_user_lga   FOREIGN KEY (lga_id)   REFERENCES lgas(id)
);

CREATE TABLE refresh_tokens (
    id          CHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    user_id     CHAR(36)    NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    device_info TEXT,
    expires_at  DATETIME(6) NOT NULL,
    revoked     TINYINT(1)  NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE otp_codes (
    id          CHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    user_id     CHAR(36)    NOT NULL,
    purpose     VARCHAR(30) NOT NULL,
    code_hash   VARCHAR(64) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    used        TINYINT(1)  NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_otp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────
-- WALLETS
-- ─────────────────────────────────────────────

CREATE TABLE wallets (
    id              CHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    user_id         CHAR(36)    NOT NULL UNIQUE,
    balance_kobo    BIGINT      NOT NULL DEFAULT 0,
    locked_kobo     BIGINT      NOT NULL DEFAULT 0,
    paystack_ref    VARCHAR(100),
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_balance_positive CHECK (balance_kobo >= 0),
    CONSTRAINT chk_locked_positive  CHECK (locked_kobo  >= 0),
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ─────────────────────────────────────────────
-- SAVINGS GROUPS
-- ─────────────────────────────────────────────

CREATE TABLE `groups` (
    id                  CHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    creator_id          CHAR(36)    NOT NULL,
    name                VARCHAR(100) NOT NULL,
    description         TEXT,
    contribution_kobo   BIGINT      NOT NULL,
    max_members         SMALLINT    NOT NULL,
    cycle_frequency     ENUM('WEEKLY', 'BIWEEKLY', 'MONTHLY')          NOT NULL DEFAULT 'MONTHLY',
    payout_order        ENUM('SEQUENTIAL', 'RANDOM', 'BIDDING')         NOT NULL DEFAULT 'SEQUENTIAL',
    start_date          DATE        NOT NULL,
    status              ENUM('OPEN', 'ACTIVE', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'OPEN',
    invite_code         CHAR(8)     NOT NULL UNIQUE,
    total_cycles        SMALLINT    NOT NULL,
    current_cycle       SMALLINT    NOT NULL DEFAULT 0,
    rules               TEXT,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at          DATETIME(6),
    CONSTRAINT chk_contribution CHECK (contribution_kobo >= 50000),
    CONSTRAINT chk_max_members  CHECK (max_members BETWEEN 2 AND 50),
    CONSTRAINT fk_group_creator FOREIGN KEY (creator_id) REFERENCES users(id)
);

CREATE TABLE group_members (
    id              CHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    group_id        CHAR(36)    NOT NULL,
    user_id         CHAR(36)    NOT NULL,
    payout_position SMALLINT,
    joined_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    left_at         DATETIME(6),
    UNIQUE KEY uq_group_member (group_id, user_id),
    CONSTRAINT fk_gm_group FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_user  FOREIGN KEY (user_id)  REFERENCES users(id)
);

-- ─────────────────────────────────────────────
-- PAYOUT CYCLES
-- ─────────────────────────────────────────────

CREATE TABLE payout_cycles (
    id              CHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    group_id        CHAR(36)    NOT NULL,
    cycle_number    SMALLINT    NOT NULL,
    recipient_id    CHAR(36)    NOT NULL,
    due_date        DATE        NOT NULL,
    total_kobo      BIGINT      NOT NULL DEFAULT 0,
    paid_out_at     DATETIME(6),
    status          ENUM('PENDING', 'COLLECTING', 'PAID_OUT', 'SKIPPED') NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_payout_cycle (group_id, cycle_number),
    CONSTRAINT fk_pc_group     FOREIGN KEY (group_id)     REFERENCES `groups`(id),
    CONSTRAINT fk_pc_recipient FOREIGN KEY (recipient_id) REFERENCES users(id)
);

-- ─────────────────────────────────────────────
-- TRANSACTIONS
-- ─────────────────────────────────────────────

CREATE TABLE transactions (
    id                  CHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    user_id             CHAR(36)    NOT NULL,
    group_id            CHAR(36),
    payout_cycle_id     CHAR(36),
    type                ENUM('DEPOSIT', 'CONTRIBUTION', 'PAYOUT', 'WITHDRAWAL', 'PENALTY', 'REFUND') NOT NULL,
    channel             ENUM('PAYSTACK', 'BANK_TRANSFER', 'WALLET', 'SYSTEM')                        NOT NULL DEFAULT 'WALLET',
    amount_kobo         BIGINT      NOT NULL,
    fee_kobo            BIGINT      NOT NULL DEFAULT 0,
    status              ENUM('PENDING', 'SUCCESS', 'FAILED', 'REVERSED') NOT NULL DEFAULT 'PENDING',
    reference           VARCHAR(100) NOT NULL UNIQUE,
    external_ref        VARCHAR(200),
    description         TEXT,
    metadata            JSON,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_amount_positive CHECK (amount_kobo > 0),
    CONSTRAINT fk_txn_user   FOREIGN KEY (user_id)         REFERENCES users(id),
    CONSTRAINT fk_txn_group  FOREIGN KEY (group_id)        REFERENCES `groups`(id),
    CONSTRAINT fk_txn_cycle  FOREIGN KEY (payout_cycle_id) REFERENCES payout_cycles(id)
);

-- ─────────────────────────────────────────────
-- NOTIFICATIONS
-- ─────────────────────────────────────────────

CREATE TABLE notifications (
    id          CHAR(36)    NOT NULL DEFAULT (UUID()) PRIMARY KEY,
    user_id     CHAR(36)    NOT NULL,
    type        ENUM('GROUP_INVITE', 'CONTRIBUTION_DUE', 'PAYOUT_RECEIVED', 'DEPOSIT_SUCCESS', 'GENERAL') NOT NULL,
    title       VARCHAR(100) NOT NULL,
    body        TEXT        NOT NULL,
    data        JSON,
    `read`      TINYINT(1)  NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────
-- INDEXES
-- (MySQL does not support partial/filtered indexes)
-- ─────────────────────────────────────────────

CREATE INDEX idx_users_email         ON users(email);
CREATE INDEX idx_users_phone         ON users(phone);
CREATE INDEX idx_users_deleted_at    ON users(deleted_at);
CREATE INDEX idx_groups_creator      ON `groups`(creator_id);
CREATE INDEX idx_groups_status       ON `groups`(status);
CREATE INDEX idx_groups_deleted_at   ON `groups`(deleted_at);
CREATE INDEX idx_group_members_group ON group_members(group_id);
CREATE INDEX idx_group_members_user  ON group_members(user_id);
CREATE INDEX idx_transactions_user   ON transactions(user_id);
CREATE INDEX idx_transactions_group  ON transactions(group_id);
CREATE INDEX idx_transactions_ref    ON transactions(reference);
CREATE INDEX idx_payout_cycles_group ON payout_cycles(group_id);
CREATE INDEX idx_notifications_user  ON notifications(user_id, `read`);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
