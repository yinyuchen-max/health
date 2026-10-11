-- 旧记录从版本 0 开始，新记录使用同一默认值。
ALTER TABLE health_record
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';
