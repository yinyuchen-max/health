CREATE TABLE user_ai_config (
    user_id BIGINT NOT NULL PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(100) NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    encrypted_embedding_api_key TEXT NULL,
    revision VARCHAR(36) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='按账号隔离的 AI 配置；API Key 仅存储 AES-GCM 密文';
