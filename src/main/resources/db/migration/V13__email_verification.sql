CREATE TABLE email_challenges (
    user_id CHAR(36) NOT NULL PRIMARY KEY,
    email VARCHAR(150) NOT NULL,
    code_hash VARCHAR(64),
    expires_at TIMESTAMP(6) NULL,
    sent_at TIMESTAMP(6) NULL,
    window_started_at TIMESTAMP(6) NULL,
    send_count INT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    verified_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_email_challenge_user FOREIGN KEY (user_id) REFERENCES users(id)
);
