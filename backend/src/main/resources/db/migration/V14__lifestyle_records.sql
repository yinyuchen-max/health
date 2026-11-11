CREATE TABLE user_health_goal (
    user_id BIGINT NOT NULL PRIMARY KEY,
    steps INT NOT NULL,
    exercise_minutes INT NOT NULL,
    water_ml INT NOT NULL,
    sleep_hours INT NOT NULL,
    calories INT NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_health_goal_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户保存的每日目标，网页和小程序共用';

CREATE TABLE sleep_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    record_date DATE NOT NULL,
    bed_time CHAR(5) NOT NULL,
    wake_time CHAR(5) NOT NULL,
    sleep_minutes INT NOT NULL,
    quality VARCHAR(8) NOT NULL,
    deep_minutes INT NULL,
    light_minutes INT NULL,
    rem_minutes INT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uq_sleep_user_date (user_id, record_date),
    CONSTRAINT fk_sleep_record_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户记录的睡眠，网页和小程序共用';

CREATE TABLE chat_reply_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    rating VARCHAR(16) NOT NULL,
    excerpt VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_chat_feedback_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户对 AI 回复的评价';
