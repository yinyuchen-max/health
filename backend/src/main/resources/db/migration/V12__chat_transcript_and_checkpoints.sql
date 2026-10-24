CREATE TABLE chat_context_state (
    user_id BIGINT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    covered_through BIGINT NOT NULL DEFAULT 0,
    checkpoint_json LONGTEXT NULL,
    lease_token VARCHAR(64) NULL,
    lease_until TIMESTAMP NULL,
    CONSTRAINT fk_chat_context_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);

CREATE TABLE chat_message_archive (
    seq BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL UNIQUE,
    turn_key VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content LONGTEXT NOT NULL,
    include_in_context BOOLEAN NOT NULL DEFAULT TRUE,
    recorded_at TIMESTAMP NULL,
    CONSTRAINT uq_chat_archive_turn_role UNIQUE(user_id,turn_key,role),
    CONSTRAINT fk_chat_archive_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_chat_archive_user_seq(user_id,seq)
);

CREATE TABLE chat_compaction_checkpoint (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    version BIGINT NOT NULL,
    covered_through BIGINT NOT NULL,
    checkpoint_json LONGTEXT NOT NULL,
    before_tokens BIGINT NOT NULL,
    after_tokens BIGINT NOT NULL,
    model VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chat_checkpoint_version UNIQUE(user_id,version),
    CONSTRAINT fk_chat_checkpoint_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);
