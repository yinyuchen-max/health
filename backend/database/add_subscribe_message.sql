-- 订阅消息表增量脚本
-- 用于存储微信订阅消息授权记录

CREATE TABLE IF NOT EXISTS subscribe_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    openid VARCHAR(128) NOT NULL,
    template_id VARCHAR(128) NOT NULL,
    status TINYINT DEFAULT 1 COMMENT '1-有效 0-已使用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_template (user_id, template_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信订阅消息授权记录';
