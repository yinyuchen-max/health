CREATE TABLE user_memory_settings (
    user_id BIGINT NOT NULL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    revision BIGINT NOT NULL DEFAULT 0,
    next_turn BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_memory_settings_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);

CREATE TABLE user_memory (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    fact_key VARCHAR(100) NOT NULL,
    category VARCHAR(32) NOT NULL,
    content VARCHAR(500) NOT NULL,
    source_text VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    pending_content VARCHAR(500) NULL,
    pending_source_text VARCHAR(1000) NULL,
    last_turn BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    CONSTRAINT uq_user_memory_fact UNIQUE (user_id, fact_key),
    CONSTRAINT fk_memory_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_user_memory_status (user_id, status)
) COMMENT='用户自述事实记忆；冲突信息需用户确认，不作为医学诊断';
