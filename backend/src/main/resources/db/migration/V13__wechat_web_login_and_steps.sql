ALTER TABLE sys_user ADD COLUMN nickname VARCHAR(64) DEFAULT NULL COMMENT '用户授权填写的昵称';

CREATE TABLE wechat_step_record (
    user_id BIGINT NOT NULL,
    record_date DATE NOT NULL,
    steps INT NOT NULL,
    synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, record_date),
    CONSTRAINT fk_wechat_steps_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信运动每日步数，两端共享';

CREATE TABLE wechat_user_avatar (
    user_id BIGINT NOT NULL PRIMARY KEY,
    avatar_key CHAR(32) NOT NULL UNIQUE,
    image_data MEDIUMBLOB NOT NULL,
    CONSTRAINT fk_wechat_avatar_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户主动选择并保存的头像';
