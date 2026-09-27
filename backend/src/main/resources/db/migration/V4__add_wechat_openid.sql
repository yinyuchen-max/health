-- 微信 openid 字段增量脚本
-- 为 sys_user 表添加微信 openid 字段，用于微信小程序登录

ALTER TABLE sys_user ADD COLUMN wechat_openid VARCHAR(64) DEFAULT NULL COMMENT '微信小程序 openid' AFTER phone;
CREATE UNIQUE INDEX idx_user_wechat_openid ON sys_user(wechat_openid);
