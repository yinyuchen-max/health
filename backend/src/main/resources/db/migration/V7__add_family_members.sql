CREATE TABLE family_binding (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_low BIGINT NOT NULL,
    user_high BIGINT NOT NULL,
    inviter_id BIGINT NOT NULL,
    invitee_id BIGINT NOT NULL,
    relationship VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_family_pair UNIQUE (user_low, user_high),
    CONSTRAINT fk_family_low FOREIGN KEY (user_low) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_family_high FOREIGN KEY (user_high) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_family_invitee (invitee_id, status),
    INDEX idx_family_inviter (inviter_id, status)
);

CREATE TABLE family_reminder (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    binding_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    read_status BOOLEAN NOT NULL DEFAULT FALSE,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_family_reminder_binding FOREIGN KEY (binding_id) REFERENCES family_binding(id) ON DELETE CASCADE,
    INDEX idx_family_reminder_recipient (recipient_id, created_at),
    INDEX idx_family_reminder_cooldown (sender_id, recipient_id, type, created_at)
);
