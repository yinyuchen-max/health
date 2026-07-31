-- 添加 role 字段到 sys_user 表（用于修复管理员身份可伪造漏洞）
-- 已有数据库执行此脚本，新数据库已通过 health_system.sql 初始化

USE health_system;

-- 添加 role 字段（如果不存在）
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'health_system' AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role');
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN role varchar(20) DEFAULT ''user'' COMMENT ''admin-管理员, user-普通用户'' AFTER avatar',
    'SELECT ''column already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 将用户名为 admin 的用户设置为管理员角色
UPDATE sys_user SET role = 'admin' WHERE username = 'admin';

-- 确保其他用户的角色为 user
UPDATE sys_user SET role = 'user' WHERE role IS NULL OR role = '';

-- 密码加密迁移说明：
-- 现有数据库中的密码是明文存储的，新注册的用户会使用 BCrypt 加密。
-- 如需迁移现有用户的密码，需要手动执行以下 SQL（将明文密码替换为 BCrypt 哈希）：
-- 注意：以下哈希值对应密码 "123456"，请根据实际密码修改
-- UPDATE sys_user SET password = '$2a$10$N9qo8uLOickgx2ZMRZoMye...BCrypt_HASH...' WHERE username = 'admin';
