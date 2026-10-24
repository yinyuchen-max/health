ALTER TABLE user_memory_settings ADD COLUMN encoding_threshold DOUBLE NOT NULL DEFAULT 0.45;
ALTER TABLE user_memory_settings ADD COLUMN relevance_weight DOUBLE NOT NULL DEFAULT 0.55;
ALTER TABLE user_memory_settings ADD COLUMN decay_multiplier DOUBLE NOT NULL DEFAULT 1.0;
ALTER TABLE user_memory_settings ADD COLUMN context_budget INT NOT NULL DEFAULT 1800;
ALTER TABLE user_memory_settings ADD COLUMN feedback_count INT NOT NULL DEFAULT 0;
ALTER TABLE user_memory_settings ADD COLUMN last_consolidated_at TIMESTAMP NULL;

ALTER TABLE user_memory ADD COLUMN semantic_version INT NOT NULL DEFAULT 1;
ALTER TABLE user_memory ADD COLUMN importance DOUBLE NOT NULL DEFAULT 0.6;
ALTER TABLE user_memory ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_memory ADD COLUMN temporal_type VARCHAR(32) NOT NULL DEFAULT 'ongoing';
ALTER TABLE user_memory ADD COLUMN last_accessed_at TIMESTAMP NULL;
ALTER TABLE user_memory ADD COLUMN useful_count INT NOT NULL DEFAULT 0;
UPDATE user_memory SET importance=CASE WHEN category IN ('health','family') THEN 0.85 WHEN category='goal' THEN 0.85 ELSE 0.7 END,
    temporal_type=CASE WHEN expires_at IS NOT NULL THEN 'temporary' WHEN category='goal' THEN 'until_changed' ELSE 'ongoing' END;

CREATE TABLE memory_outbox (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    turn_no BIGINT NOT NULL,
    task_type VARCHAR(20) NOT NULL,
    input_text TEXT NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_publish_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMP NULL,
    lease_token VARCHAR(36) NULL,
    last_error VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT uq_memory_job UNIQUE(user_id,turn_no,task_type),
    CONSTRAINT fk_memory_job_user FOREIGN KEY(user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_memory_outbox_due(state,next_publish_at,available_at)
);

CREATE TABLE memory_episode (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_id VARCHAR(36) NOT NULL,
    turn_no BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    fact_key VARCHAR(100) NOT NULL,
    category VARCHAR(32) NOT NULL,
    content VARCHAR(500) NOT NULL,
    evidence VARCHAR(1000) NOT NULL,
    evidence_hash VARCHAR(64) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    subject VARCHAR(16) NOT NULL,
    temporal_type VARCHAR(32) NOT NULL,
    explicit_change BOOLEAN NOT NULL DEFAULT FALSE,
    goal VARCHAR(500) NULL,
    importance DOUBLE NOT NULL,
    attention DOUBLE NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    occurred_at TIMESTAMP NULL,
    time_evidence VARCHAR(200) NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    consolidated BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_memory_episode UNIQUE(job_id,fact_key),
    CONSTRAINT fk_memory_episode_user FOREIGN KEY(user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_memory_episode_user(user_id,state,recorded_at),
    INDEX idx_memory_episode_key(user_id,fact_key,turn_no)
);

CREATE TABLE memory_semantic_version (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    memory_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    content VARCHAR(500) NOT NULL,
    source_text VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    pending_content VARCHAR(500) NULL,
    valid_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_to TIMESTAMP NULL,
    CONSTRAINT uq_memory_version UNIQUE(memory_id,version_no),
    CONSTRAINT fk_memory_version_fact FOREIGN KEY(memory_id) REFERENCES user_memory(id) ON DELETE CASCADE
);

CREATE TABLE memory_evidence (
    user_id BIGINT NOT NULL,
    memory_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    episode_id VARCHAR(36) NOT NULL,
    relation VARCHAR(16) NOT NULL,
    PRIMARY KEY(memory_id,version_no,episode_id),
    CONSTRAINT fk_memory_evidence_fact FOREIGN KEY(memory_id) REFERENCES user_memory(id) ON DELETE CASCADE,
    CONSTRAINT fk_memory_evidence_episode FOREIGN KEY(episode_id) REFERENCES memory_episode(id) ON DELETE CASCADE
);

CREATE TABLE memory_entity (
    user_id BIGINT NOT NULL,
    entity VARCHAR(64) NOT NULL,
    memory_type VARCHAR(16) NOT NULL,
    memory_id VARCHAR(36) NOT NULL,
    PRIMARY KEY(user_id,entity,memory_type,memory_id),
    INDEX idx_memory_entity_node(user_id,memory_type,memory_id),
    CONSTRAINT fk_memory_entity_user FOREIGN KEY(user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);

CREATE TABLE memory_vector (
    user_id BIGINT NOT NULL,
    memory_type VARCHAR(16) NOT NULL,
    memory_id VARCHAR(36) NOT NULL,
    version_no INT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    model VARCHAR(100) NOT NULL,
    dimension_no INT NOT NULL,
    vector_json TEXT NOT NULL,
    PRIMARY KEY(user_id,memory_type,memory_id),
    CONSTRAINT fk_memory_vector_user FOREIGN KEY(user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);

CREATE TABLE memory_retrieval (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    turn_no BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    feedback VARCHAR(24) NULL,
    CONSTRAINT uq_memory_retrieval UNIQUE(user_id,turn_no),
    CONSTRAINT fk_memory_retrieval_user FOREIGN KEY(user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);

CREATE TABLE memory_retrieval_item (
    retrieval_id VARCHAR(36) NOT NULL,
    memory_type VARCHAR(16) NOT NULL,
    memory_id VARCHAR(36) NOT NULL,
    version_no INT NOT NULL,
    score DOUBLE NOT NULL,
    reason VARCHAR(100) NOT NULL,
    PRIMARY KEY(retrieval_id,memory_type,memory_id),
    CONSTRAINT fk_memory_retrieval_item FOREIGN KEY(retrieval_id) REFERENCES memory_retrieval(id) ON DELETE CASCADE
);

-- Existing user-confirmed/self-reported facts retain provenance and version 1.
INSERT INTO memory_semantic_version(id,user_id,memory_id,version_no,content,source_text,status,pending_content,valid_from)
SELECT CONCAT('legacy-',id),user_id,id,1,content,source_text,status,pending_content,updated_at FROM user_memory;
